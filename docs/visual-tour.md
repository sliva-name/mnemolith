# Visual tour (dev only)

A client-side harness that builds a fresh creative world, stages each scene on a sky platform and saves real
rendered screenshots. It is off by default and does nothing unless the JVM gets `-Dmnemolith.visualTour=true`.
It never injects input; it drives the game through its own screens and the integrated server's command dispatcher.

```
JAVA_TOOL_OPTIONS="-Dmnemolith.visualTour=true" ./gradlew runClient
```

Optional properties:

- `mnemolith.visualTour.dir`: output folder (default `/workspace/visual-tour`), one `<scene>.png` each.
- `mnemolith.visualTour.only`: comma-separated scene name prefixes.
- `mnemolith.visualTour.lang`: language code such as `ru_ru`; files become `<scene>.<lang>.png`.

Headless boxes: run under Xvfb with `LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe`. The game quits when done.

Comparison scenes: `edge_baseline` vs `circlet_tint`, `strong_baseline` vs `strong_hint`, and the pressure vignette on its
own (`overloaded_vignette`, `fracture_vignette`) and stacked with the hint (`hint_plus_overloaded`).
