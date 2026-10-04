# Labelling one chunk

A chunk file has two parts:

- **Entries each word could be**: every word to check, with its options as letters. Each option
  shows the headword, part of speech, how this spelling relates to it (dictionary form, accusative
  singular, …) and its first meanings.
- **Sentences**: each with its Tatoeba id, the Hungarian, the English translation (`en:`) and the
  words to check in it (`check:`).

For every word in every `check:` list, decide which entry that word **is in this sentence**. Use
the English translation as the main evidence.

## Answer file

Write `data/sentence-labels/answers/NNNN.txt` with one line per sentence: the id, then
`word=letters` for each checked word, separated by spaces. No other text is needed.

```
522332 a=a sikerült=a megtalálni=a bűnözőt=b
537137 addig=a a=a üsd=a vasat=b amíg=a meleg=a
538704 a=a kéred=b vagy=b
```

## How to choose

- **One letter** for the entry the word is. *tart* in "Az utazás öt napig tart" ("the trip takes
  five days") is the verb *tart*, not the accusative of *tar* "bald".
- **Several letters** (`=ab`) only when the options are really the same word that the dictionary
  splits: an adverb and noun with the same meaning (*tegnap* "yesterday"), or an alternative
  form of the same word (*dicsekszik* / *dicsekedik*). Never for different meanings.
- **`-`** when no option is right:
  - the word belongs to an entry that isn't listed (another word, or a name that isn't there);
  - the word goes with a separated verb prefix and that verb means something none of the options
    mean (*fel … hívni* "to phone" when only *hív* "to call, name" is offered). If the meaning is
    still the same (*el fog menni*, *megy* "to go"), choose the option.
- *a* / *az*: the article "the" before a noun phrase; *det* or *pron* "that" when it points or stands alone.
- *egy*: the article "a, an" unless it clearly means the number "one", "same" or "about".
- *vagy*: the conjunction "or", unless it is "you are" (*van*) or "about".
- Don't overthink rare edge cases: pick the best fit and move on. Don't write explanations.

Then run `python3 scripts/sentence_labels.py save NNNN`. If it lists problems (a missing word, a
letter that isn't an option, an unreadable line), fix those lines in the answer file and run save
again until it prints `chunk NNNN saved`.
