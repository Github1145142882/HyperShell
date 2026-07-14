# Open-source attribution

HyperShell's Miuix page structure, pager navigation, grouped-card layout, and Navigation 3
back-stack approach are adapted from
[chenaizhang/KernelSU-Style-UI-Kit](https://github.com/chenaizhang/KernelSU-Style-UI-Kit),
revision `c90d322c8c6d9d8f591490c5fdb39ff9f6b5210e`.

KernelSU-Style-UI-Kit is distributed under the GNU General Public License v3.0.
Miuix is distributed under its upstream open-source license. HyperShell retains the
corresponding copyright and license notices for adapted portions.

HyperShell can optionally use MiSans. The font is not bundled with HyperShell and is
not redistributed by this repository or its package feed. After the user accepts the
MiSans intellectual-property license, HyperShell downloads MiSans Regular directly
from Xiaomi's official server and stores it only in the application's private data.
MiSans and its name are the property of Xiaomi. The official download and license are
available at https://hyperos.mi.com/font/zh/download/.

HyperShell builds `terminal-view` from the pinned `termux/termux-app` source and carries
a local `TerminalRenderer` modification for Android HDR text output. The upstream module
and its Android Terminal Emulator portions retain their original GPLv3/Apache-2.0 notices.
