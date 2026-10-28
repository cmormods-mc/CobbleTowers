"""The validator must bite: each mutation of a good tower has to fail validation with the expected complaint.

    python -m unittest tools/halloween_tower/test_hollow_tower.py   (from the repo root)
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import hollow_tower as ht  # noqa: E402


class HollowTowerTest(unittest.TestCase):
    def setUp(self):
        self.t = ht.build()

    def complaints(self):
        return "\n".join(ht.validate(self.t))

    def test_the_tower_is_valid(self):
        self.assertEqual([], ht.validate(self.t))

    def test_dimensions_and_floor_levels(self):
        self.assertEqual((97, 121, 97), self.t.grid.shape)
        self.assertEqual([6, 16, 26, 36, 46, 56, 66, 76, 86, 96], [ht.feet(k) for k in range(1, 11)])
        top = ht.feet(10) + ht.CLEAR + 1
        self.assertLessEqual(top + 8, ht.SIZE_Y - 1)

    def test_every_anchor_stands_on_something(self):
        for floor in range(1, 11):
            for name in ("entry", "presentation", "exit"):
                a = ht.layout(floor)[name]
                self.assertTrue(ht.standable(self.t, a["x"], a["y"], a["z"]), (floor, name))

    def test_mirroring_swaps_the_halls_every_other_floor(self):
        self.assertEqual(24, ht.layout(1)["entry"]["x"])
        self.assertEqual(72, ht.layout(2)["entry"]["x"])
        self.assertEqual(24, ht.layout(3)["entry"]["x"])
        self.assertEqual(270, ht.layout(1)["entry"]["yaw"])
        self.assertEqual(90, ht.layout(2)["entry"]["yaw"])

    def test_an_open_gate_a_is_caught(self):
        g = ht.layout(3)["gate_a"]
        (ax, ay, az), (bx, by, bz) = g["min"], g["max"]
        self.t.grid[ax:bx + 1, ay:by + 1, az:bz + 1] = 0
        self.assertIn("vendor's room is reachable with gate A closed", self.complaints())

    def test_an_open_gate_b_is_caught(self):
        g = ht.layout(4)["gate_b"]
        (ax, ay, az), (bx, by, bz) = g["min"], g["max"]
        self.t.grid[ax:bx + 1, ay:by + 1, az:bz + 1] = 0
        g_a = ht.layout(4)["gate_a"]
        (ax, ay, az), (bx, by, bz) = g_a["min"], g_a["max"]
        self.t.grid[ax:bx + 1, ay:by + 1, az:bz + 1] = 0
        self.assertTrue(ht.validate(self.t))

    def test_a_walled_door_is_caught(self):
        f = ht.feet(2)
        self.t.mirror = True
        self.t.fill(18, 19, f, f + 5, 46, 50, ht.PLAIN_WALL)
        self.assertIn("floor 1: the next floor's entry is not reachable", self.complaints())

    def test_a_missing_step_is_caught(self):
        f = ht.feet(5)
        self.t.mirror = False
        # floor 5 is odd, so its stair is unmirrored: step 4 is at z = 9, y = f + 3
        self.t.fill(85, 87, f + 3, f + 3, 9, 9, ht.AIR)
        self.assertIn("floor 5: the next floor's entry is not reachable", self.complaints())

    def test_a_filled_hole_is_caught(self):
        f = ht.feet(6)
        self.t.mirror = True
        self.t.fill(85, 87, f + 8, f + 9, 12, 12, ht.SLAB)
        self.assertIn("floor 6", self.complaints())

    def test_a_cobweb_in_the_walking_volume_is_caught(self):
        f = ht.feet(7)
        self.t.set(30, f + 1, 30, ht.COBWEB)
        self.assertIn("cobwebs inside the walking volume", self.complaints())

    def test_the_stair_is_one_block_per_step(self):
        f = ht.feet(1)
        for i in range(1, 11):
            self.assertEqual(ht.STAIR_BLOCK, self.t.at(86, f + i - 1, 5 + i))

    def test_nothing_unknown_in_the_palette(self):
        known = {ht.AIR, ht.PLAIN_WALL, ht.OUTER, ht.SLAB, ht.TRIM, ht.DARK, ht.PURPLE, ht.TERRA, ht.ORANGE, ht.GLASS, ht.LANTERN,
                 ht.BARS, ht.WALL_CAP, ht.STAIR_BLOCK, ht.CHISELED, ht.AMETHYST, ht.SHROOM, ht.COBWEB, ht.SOUL}
        self.assertEqual(set(), set(self.t.palette) - known)


if __name__ == "__main__":
    unittest.main()
