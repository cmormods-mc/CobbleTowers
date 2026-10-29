# Jack-o'-Lantern Vault (Halloween trick-or-treat reward chest)

A finished model, textures, animation spec and runtime for the Halloween reward chest: separate rigid pieces drawn by Polymer item displays,
server-controlled, no client mod. Start with `art/jack_o_lantern_vault/README.md`; the final report with what was and was not tested is
`art/jack_o_lantern_vault/HANDOFF-REPORT.md`, the concept comparison is `art/jack_o_lantern_vault/VISUAL-CHECKLIST.md`.

* Regenerate: `python3 tools/vault/vault_build.py`; check: `python3 tools/vault/vault_verify.py` (36 checks).
* Runtime: `com.cobbletowers.halloween.vault` (`VaultService.spawn / start / close / reset / remove`, listeners for `reward_trigger` and `encounter_trigger`);
  operator command `/cobbletowers vault ...`.

* 2026-10-29: art replaced by the Hallowbound Reliquary v3.1 (`tools/vault/import_reliquary.py`); the generators above are superseded. The vault is wired to Trick or Treat (`com.cobbletowers.trick`) and opens by clicking it (a vote).
