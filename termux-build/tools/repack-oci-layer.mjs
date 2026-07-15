import fs from 'node:fs'
import { createGunzip, createGzip } from 'node:zlib'
import { extract, pack } from 'tar-stream'

const [input, output, caBundle, packageManifest] = process.argv.slice(2)
if (!input || !output || !caBundle || !packageManifest) {
  throw new Error('usage: repack-oci-layer.mjs INPUT.tar.gz OUTPUT.tar.gz CA-BUNDLE PACKAGES.txt')
}

const reader = extract()
const writer = pack()
const regularFiles = new Map()
const pendingLinks = []
const seen = new Set()
let dpkgStatus = null

reader.on('entry', (header, stream, next) => {
  const name = header.name.replace(/^\.\//, '')
  if (!name && header.type === 'directory') {
    stream.resume()
    stream.on('end', next)
    return
  }
  if (!name || name.startsWith('/') || name.split('/').includes('..')) {
    stream.resume()
    throw new Error(`unsafe OCI layer path: ${header.name}`)
  }
  seen.add(name)
  if (header.type === 'link') {
    pendingLinks.push({ ...header, name })
    stream.resume()
    stream.on('end', next)
    return
  }
  const chunks = []
  stream.on('data', chunk => chunks.push(chunk))
  stream.on('end', () => {
    const data = Buffer.concat(chunks)
    if (header.type === 'file') {
      regularFiles.set(name, data)
      if (name === 'var/lib/dpkg/status') dpkgStatus = data.toString('utf8')
    }
    writer.entry({ ...header, name, uid: 0, gid: 0, uname: '', gname: '', mtime: new Date(0) }, data, next)
  })
})
reader.on('finish', () => {
  for (const header of pendingLinks) {
    const target = header.linkname?.replace(/^\.\//, '')
    const data = regularFiles.get(target)
    if (!data) throw new Error(`unresolved hard link: ${header.name} -> ${header.linkname}`)
    writer.entry({ ...header, type: 'file', linkname: null, size: data.length, uid: 0, gid: 0, uname: '', gname: '', mtime: new Date(0) }, data)
  }
  const addFile = (name, data, mode = 0o644) => {
    if (!seen.has(name)) writer.entry({ name, type: 'file', mode, uid: 0, gid: 0, uname: '', gname: '', mtime: new Date(0), size: data.length }, data)
  }
  addFile('etc/ssl/certs/ca-certificates.crt', fs.readFileSync(caBundle))
  addFile('etc/locale.gen', Buffer.from('C.UTF-8 UTF-8\n'))
  addFile('etc/default/locale', Buffer.from('LANG=C.UTF-8\n'))
  if (!dpkgStatus) throw new Error('Debian dpkg status missing')
  const packages = [...dpkgStatus.matchAll(/^Package: (.+)\n(?:.|\n)*?^Version: (.+)$/gm)]
    .map(match => `${match[1]}=${match[2]}`).sort()
  fs.writeFileSync(packageManifest, packages.join('\n') + '\n')
  writer.finalize()
})

const gzip = createGzip({ level: 9, mtime: 0 })
writer.pipe(gzip).pipe(fs.createWriteStream(output))
fs.createReadStream(input).pipe(createGunzip()).pipe(reader)
