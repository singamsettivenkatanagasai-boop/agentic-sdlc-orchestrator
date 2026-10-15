package com.agentic.orchestrator.workspace;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checkpoints as git commits in a repository that lives inside the workspace itself.
 * The commit history doubles as a reviewable change log of what the agents did.
 */
public final class JGitCheckpointStore implements CheckpointStore {
    private static final PersonIdent IDENTITY = new PersonIdent("agentic-orchestrator", "orchestrator@localhost");

    @Override
    public synchronized String checkpoint(Path root, String label) throws IOException {
        try (Git git = open(root)) {
            git.add().addFilepattern(".").call();                    // new + modified files
            git.add().addFilepattern(".").setUpdate(true).call();    // deletions of tracked files
            RevCommit c = git.commit().setMessage(label).setAuthor(IDENTITY).setCommitter(IDENTITY)
                    .setAllowEmpty(true).setSign(false).call();
            return c.getName();
        } catch (GitAPIException e) {
            throw new IOException("checkpoint failed: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void restore(Path root, String checkpointId) throws IOException {
        try (Git git = Git.open(root.toFile())) {
            // Git decides "unchanged" from size + timestamp. A file rewritten with the same size within
            // the same timestamp tick (typical for fast agents) would be skipped by the reset and keep
            // the new content. Dropping the index forces every file to be compared and restored.
            Files.deleteIfExists(root.resolve(".git").resolve("index"));
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef(checkpointId).call();
            git.clean().setCleanDirectories(true).call();
        } catch (GitAPIException e) {
            throw new IOException("restore failed: " + e.getMessage(), e);
        }
    }

    private static Git open(Path root) throws IOException, GitAPIException {
        if (!Files.exists(root.resolve(".git"))) {
            try (Git created = Git.init().setDirectory(root.toFile()).call()) {
                // Only at creation: rewriting .git/config on every checkpoint would race with readers.
                isolateFromUserGitConfig(created);
            }
            Path ignore = root.resolve(".gitignore");
            if (!Files.exists(ignore)) {
                Files.writeString(ignore, "target/\n.idea/\n*.iml\n", StandardCharsets.UTF_8);
            }
        }
        return Git.open(root.toFile());
    }

    /**
     * Checkpoint commits must never depend on the developer's global git setup. A global
     * {@code gpg.format=ssh} or {@code commit.gpgsign=true} (very common) would otherwise make
     * JGit fail or try to sign. Repository-level settings override the user and system ones.
     */
    private static void isolateFromUserGitConfig(Git git) throws IOException {
        StoredConfig cfg = git.getRepository().getConfig();
        cfg.setString("gpg", null, "format", "openpgp");
        cfg.setBoolean("commit", null, "gpgsign", false);
        cfg.setBoolean("tag", null, "gpgsign", false);
        cfg.save();
    }
}
