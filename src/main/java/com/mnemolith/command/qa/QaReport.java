package com.mnemolith.command.qa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.mnemolith.Mnemolith;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * The outcome of one QA suite: named checks in order, plus free-form notes. The {@code /mnemolith ...qa} commands
 * send it to chat; the game tests ({@code com.mnemolith.gametest}) fail with the names of the checks that did not pass.
 */
public final class QaReport {
    private final String suite;
    private final String[] names;
    private final boolean[] checks;
    private final List<String> notes;

    public QaReport(String suite, String[] names, boolean[] checks, List<String> notes) {
        this.suite = suite;
        this.names = names.clone();
        // A suite that stops early reports fewer flags than names: the missing ones count as failed.
        this.checks = Arrays.copyOf(checks, names.length);
        this.notes = List.copyOf(notes);
    }

    public String suite() {
        return this.suite;
    }

    public int total() {
        return this.names.length;
    }

    public int passed() {
        return QaSupport.count(this.checks);
    }

    public boolean allPassed() {
        return passed() == total();
    }

    public List<String> failed() {
        List<String> failed = new ArrayList<>();
        for (int i = 0; i < this.names.length; i++) {
            if (!this.checks[i]) {
                failed.add(this.names[i]);
            }
        }
        return failed;
    }

    public List<String> notes() {
        return this.notes;
    }

    /** {@code name=true name=false ...} in check order. */
    public String line() {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < this.names.length; i++) {
            line.append(this.names[i]).append('=').append(this.checks[i]).append(' ');
        }
        return line.toString().trim();
    }

    /** Logs the line and every note under {@code Mnemolith <suite>}, the format the QA checklist greps for. */
    QaReport log() {
        Mnemolith.LOGGER.info("Mnemolith {} {}", this.suite, line());
        for (String note : this.notes) {
            Mnemolith.LOGGER.info("Mnemolith {} note {}", this.suite, note);
        }
        return this;
    }

    /** Sends the notes (optionally), the check line, and {@code mnemolith.command.<suite>} with passed/total. */
    int send(CommandSourceStack source, boolean notesToChat) {
        if (notesToChat) {
            for (String note : this.notes) {
                source.sendSuccess(() -> Component.literal(note), false);
            }
        }
        String summary = line();
        int passed = passed();
        int total = total();
        source.sendSuccess(() -> Component.literal(summary), false);
        source.sendSuccess(() -> Component.translatable("mnemolith.command." + this.suite, passed, total), true);
        return passed;
    }
}
