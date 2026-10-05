package com.cobbletowers.persistence;

import com.cobbletowers.mastery.LeaderboardRules.Entry;
import com.cobbletowers.mastery.LeaderboardRules.Member;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** How one leaderboard entry is written to NBT, shared by the live boards and the Hall of Fame (P36a) so there is one encoding. */
public final class BoardCodec {

    private BoardCodec() {}

    public static Entry read(CompoundTag tag) {
        List<Member> players = new ArrayList<>();
        ListTag members = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < members.size(); i++) {
            CompoundTag member = members.getCompound(i);
            players.add(new Member(member.getUUID("id"), member.getString("name")));
        }
        UUID run = tag.hasUUID("run") ? tag.getUUID("run") : null;
        return new Entry(players, tag.getLong("value"), run, tag.getInt("ascension"), tag.getInt("score"),
                tag.getInt("ruleset_revision"), tag.getInt("tower_revision"), tag.getString("tower_digest"), tag.getLong("at"));
    }

    public static CompoundTag write(Entry entry) {
        CompoundTag tag = new CompoundTag();
        ListTag members = new ListTag();
        for (Member member : entry.players()) {
            CompoundTag memberTag = new CompoundTag();
            memberTag.putUUID("id", member.id());
            memberTag.putString("name", member.name());
            members.add(memberTag);
        }
        tag.put("players", members);
        if (entry.runId() != null) tag.putUUID("run", entry.runId());
        tag.putLong("value", entry.value());
        tag.putInt("ascension", entry.ascension());
        tag.putInt("score", entry.score());
        tag.putInt("ruleset_revision", entry.rulesetRevision());
        tag.putInt("tower_revision", entry.towerRevision());
        tag.putString("tower_digest", entry.towerDigest());
        tag.putLong("at", entry.at());
        return tag;
    }

}
