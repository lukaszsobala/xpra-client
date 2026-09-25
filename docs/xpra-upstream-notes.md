# Xpra server issues found while writing this client

Problems in the Xpra server which this client works around. Not reported upstream yet:
we may contribute fixes later.

## x264 streams describe their colours wrongly

Seen with Xpra 6.5.3 and 6.5.4 (`xpra/codecs/x264/encoder.pyx`).

- The frames are converted from RGB by `csc_cython` with the BT.601 matrix, in full range,
  and each frame says so in its options (`"full-range": True`).
- The sequence parameter set (SPS) of the stream says otherwise:
  - `video_full_range_flag = 0` (limited range): the encoder is initialised with
    `full_range = 0`, and only switches to the range of the images after writing its headers,
  - `matrix_coefficients = 0` (identity, as if the planes held G, B and R), as
    `param.vui.i_colmatrix = 0` is hardcoded.
- Xpra's own client converts the frames itself, from the options, so it does not notice.
  Decoders which follow the SPS (Android's MediaCodec, ffmpeg) expand the colours as if they
  were limited range: darks turn black and whites clip. A light grey background of
  246,245,243 decodes as 255,255,255 (`ffprobe` reports `color_range=tv, color_space=gbr`).

Workaround in this client: `xpra.video.H264Headers` rewrites the SPS of each frame before
decoding, to full range (from the frame's options) and BT.601 (`matrix_coefficients = 6`).

A fix upstream would be to write the actual range and matrix in the VUI, ie: set
`i_colmatrix` to 6 (BT.601) and `b_fullrange` from the first image before opening the
encoder (or reopen it when the range changes).

## Other notes

- Starting a command as not shared (`start-command` with `sharing=False`) adds a window filter
  bound to the uuid of the client's hello, which is new for every connection: the windows of
  the command are then hidden after reconnecting. This client starts commands as shared.
- The menu of applications is only read when a server can parse a menu file in
  `/etc/xdg/menus` (ie: from `lxmenu-data`), even though it could list `/usr/share/applications`
  without one. Icons found as XPM files in `/usr/share/pixmaps` are sent as no icon when Pillow
  cannot read them, before the icon theme is tried (`XPRA_XDG_LOAD_FROM_PIXMAPS=0` avoids it).
