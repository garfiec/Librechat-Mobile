# feature:conversations:ui

Stateless conversations UI: the drawer (`DrawerContent(uiState…)`, `DrawerAccountFooter`), the account
switcher sheet, search, projects, bookmarks and archive contents, their view state and all of the
feature's strings. The general rules (dependencies, wrapper + Content, file names, `Res`) are in the root `CLAUDE.md`, "Feature UI modules".

- The drawer's stateful `DrawerContent` overload lives in `:feature:conversations` (`DrawerContentHost.kt`)
  and fills the footer slot; shared calls both overloads by the same name.
