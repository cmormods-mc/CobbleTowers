# Partner sprites

One 48x32 icon per Pokemon species (1,025 of them) plus `manifest.json`, which maps each file to the path it came from. They are the
default (plain, non-shiny) entity icons of the **Cobblemon Cards** mod, jar `cobblemon-cards-fabric-1.0.4`, copied unchanged by
`validation/extract_card_sprites.py`.

- Source: https://github.com/Howlite-UI/CobblemonCards
- Licence: **CC0 1.0 Universal** (the jar's `fabric.mod.json` says `CC0-1.0`; the owner of this repository confirmed it). No attribution is
  required; the source is recorded here anyway.
- Files are named by the species id with everything but letters and digits removed (`mrmime`, `hooh`), which is what the client does to an id.
- A species that only has gendered icons gets the male one (the female one if that is all there is).
- Not bundled yet: shiny icons (the God Pack is where they would matter) and alternate forms.
- `blastoise`, `machamp` and `scizor` were here first and are the same pictures.
