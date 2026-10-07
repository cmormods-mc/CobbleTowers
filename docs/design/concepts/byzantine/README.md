# Pixelated Byzantine concept art

Pictures to react to, and the scripts that draw them. `python rental_card.py` writes the mockups next to it; `python rental_card.py --bake`
writes the real textures the Rental Draft card is drawn from (`src/main/resources/assets/cobbletowers/textures/gui/byzantine`).
`generate.py` holds the shared tile and drawing code (it also draws an earlier modifier-draft concept that was not pursued).

The generated mockups (`*.png` here) are not committed; run the script. The baked game textures are.

Layout constants in `rental_card.py` (`FIELD`, `PLATE`, `TEXT`) are mirrored in `ByzantineCardFace.java`; change one, change the other.
