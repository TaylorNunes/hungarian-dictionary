---
name: label-sentences
description: Work through the queue of Tatoeba example sentences, checking which dictionary entry each word really is, a chunk at a time. Use when the user asks to label, check or clean up example sentences, or to continue the sentence queue.
---

# Label example sentences

The build gives an example sentence to every entry with a matching form, so a sentence about
*tart* "to keep" also shows up under *tar* "bald" (accusative *tart*). This queue fixes that: for
each word in a sentence, pick the entry it really is. Answers go into `data/sentence_labels.jsonl`,
which the build reads.

Progress is saved per chunk, so stopping at any point (or running out of usage) loses at most the
chunk in progress. Next time, just run this skill again.

## Loop

1. Run `python3 scripts/sentence_labels.py next`.
   - If it says the queue is empty, stop and tell the user.
   - If the queue folder doesn't exist yet, run `python3 scripts/sentence_labels.py prepare` first
     (needs `public/data` and `data_cache` from `python3 scripts/build_data.py`).
2. Hand the chunk to a subagent (Agent tool, `general-purpose`, foreground) with this prompt,
   filling in the number:

   > Label chunk NNNN. Follow `.claude/skills/label-sentences/labelling.md` exactly: read
   > `data/sentence-labels/queue/NNNN.txt`, write `data/sentence-labels/answers/NNNN.txt`, then run
   > `python3 scripts/sentence_labels.py save NNNN` and fix the answer file until it saves.
   > Reply with only the save output.

3. Report the one-line result to the user (e.g. `chunk 0007 saved … 1009 chunks left`) and go back
   to step 1. Keep going until the queue is empty or the user stops you; don't ask between chunks.

Don't commit. `data/sentence_labels.jsonl` is the saved work; the user commits it when they like
(the deployed site only uses committed labels). `python3 scripts/sentence_labels.py status` shows
overall progress.
