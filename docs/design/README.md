# Payment-success topographic pattern

`success_bg_topo.svg` is the source vector for the faint contour-line pattern
behind the payment-success card (Figma "My Cart Page" success, node
`40008282:24479`, frame `Shape` `40008282:24481`). 38 stroked contour paths in
`#292929`, viewBox `0 0 1632.7 1847.93`.

Rendered to the Android drawable (transparent PNG) with:

    rsvg-convert -b none -w 1632 -h 1848 success_bg_topo.svg \
      -o ../../app/src/main/res/drawable-nodpi/img_success_bg_topo.png

In Compose the pattern is drawn at `offset(-122dp, -418dp)`, `size(1632dp,
1848dp)`, `alpha(0.07f)`, and tinted `#292929` in light mode / `White` in dark
mode so the faint lines survive on either background.

Shared for Swift + Laravel parity (same asset, each client tints per theme).
