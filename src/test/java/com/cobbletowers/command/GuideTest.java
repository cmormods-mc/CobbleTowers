package com.cobbletowers.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The player guide behind /tower help and the Hall's Guide page (P48). */
class GuideTest {

    @Test
    @DisplayName("the guide file has a title and sections with short lines, and names the typed commands players still have")
    void guide() {
        assertFalse(HelpCommand.title().isBlank());
        var sections = HelpCommand.sections();
        assertTrue(sections.size() >= 6, "sections: " + sections.size());
        StringBuilder all = new StringBuilder();
        for (HelpCommand.Section section : sections) {
            assertFalse(section.heading().isBlank());
            assertFalse(section.lines().isEmpty(), section.heading());
            for (String line : section.lines()) {
                assertTrue(line.length() < 260, "too long to read in chat: " + line);
                all.append(line).append('\n');
            }
        }
        for (String command : new String[] {"/tower help", "/tower report"}) {
            assertTrue(all.toString().contains(command), "the guide never mentions " + command);
        }
    }
}
