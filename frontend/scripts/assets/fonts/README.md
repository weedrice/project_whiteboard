# OG build font

- `NotoSansKR-Regular.otf`: official static Korean subset from [`notofonts/noto-cjk`](https://github.com/notofonts/noto-cjk/blob/main/Sans/SubsetOTF/KR/NotoSansKR-Regular.otf)
- `OFL.txt`: SIL Open Font License 1.1 notice distributed with Noto Sans KR

The static OTF is used because Satori's current font parser cannot read the official variable `NotoSansKR[wght].ttf`. Satori supports both TTF and OTF build-time fonts; this asset is never shipped to the browser.
