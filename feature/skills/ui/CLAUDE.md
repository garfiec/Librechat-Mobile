# feature:skills:ui

Stateless skills UI: list, detail, editor and file screen contents, cards, the files section, the share
sheet, dialogs, `SkillError`, labels, validation, frontmatter parsing, the view state and all of the
feature's strings. The general rules (dependencies, wrapper + Content, file names, `Res`) are in the root `CLAUDE.md`, "Feature UI modules".

- The entry points own the snackbar events and the platform file pickers and bind them to the `…Actions`
  classes here.
