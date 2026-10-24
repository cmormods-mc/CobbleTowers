# Byzantine card style (archived)

The rental cards, the card back and the sealed pack used to be drawn from these textures: a gold-and-lapis Byzantine frame with a nimbus
behind each Pokemon. The Prism Pull card replaced them (2026-10-24, `docs/design/P33-rental-draft.md`), and the pack, the card back and
the kept-card mark are now drawn in code (`web/src/styles/cards.css`, `rcard.css`). These files are **not built, not shipped and not
referenced** by anything outside this folder.

- `textures/` is the old `assets/cobbletowers/textures/gui/byzantine` folder: `field_<rarity>.png` and `frame_<rarity>.png` (the card
  layers), `back.png`, `pack_top.png`, `pack_body.png`, `gems.png` (7x7 type gems), `seal.png`, and the README that says how they were baked.
- The generator that baked them is `docs/design/concepts/byzantine/` (kept with the other design concepts).

To bring the style back: `git mv archive/byzantine/textures src/main/resources/assets/cobbletowers/textures/gui/byzantine`, add
`'byzantine'` to the texture-kit loop in `web/build.mjs`, and restore the old `RentalCard.js` and `cards.css` from the history before the
Prism Pull commit (`6ab9537`).
