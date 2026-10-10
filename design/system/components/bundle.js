/* @ds-bundle: {"format":4,"namespace":"Tchat","components":[{"name":"Button"},{"name":"TextField"},{"name":"Chip"},{"name":"Switch"},{"name":"SegmentedControl"},{"name":"ListGroup"},{"name":"Avatar"},{"name":"Message"},{"name":"Composer"},{"name":"Banner"},{"name":"Dialog"},{"name":"EmptyState"}]} */
(function () {
  "use strict";
  var React = window.React;
  var h = React.createElement;
  var useState = React.useState;
  var useId = React.useId || function () { return "tc-" + Math.random().toString(36).slice(2, 8); };

  function cx() {
    var out = [];
    for (var i = 0; i < arguments.length; i++) if (arguments[i]) out.push(arguments[i]);
    return out.join(" ");
  }

  // Material Symbols (Filled), 24dp grid — the same set the app imports from Icons.Filled.
  var PATHS = {
    add: "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z",
    check: "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z",
    close: "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z",
    chevron: "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z",
    error: "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z",
    warning: "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z",
    success: "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z",
    info: "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z",
    send: "M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z",
    stop: "M6 6h12v12H6z",
    mic: "M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z",
    search: "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
    chat: "M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z",
    "delete": "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z",
    person: "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z",
    bell: "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z",
    copy: "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z",
    contrast: "M12 22c5.52 0 10-4.48 10-10S17.52 2 12 2 2 6.48 2 12s4.48 10 10 10zm1-17.93c3.94.49 7 3.85 7 7.93s-3.05 7.44-7 7.93V4.07z"
  };

  function Icon(props) {
    var size = props.size || 24;
    return h("svg", {
      className: cx("tc-icon", props.className),
      width: size, height: size, viewBox: "0 0 24 24",
      "aria-hidden": props.label ? undefined : "true",
      "aria-label": props.label, role: props.label ? "img" : undefined,
      focusable: "false"
    }, h("path", { d: PATHS[props.name] || PATHS.info, fill: "currentColor" }));
  }

  // ---------------------------------------------------------------- Button
  function Button(props) {
    var variant = props.variant || "filled";
    var size = props.size || "md";
    var iconOnly = props.icon && !props.children;
    return h("button", {
      type: props.type || "button",
      className: cx("tc-btn", "label-lg", "tc-btn-" + variant, "tc-btn-" + size, props.tone === "danger" && "tc-btn-danger", iconOnly && "tc-btn-icon", props.loading && "is-loading", props.fullWidth && "tc-btn-block"),
      disabled: props.disabled,
      "aria-busy": props.loading ? "true" : undefined,
      "aria-label": iconOnly ? props.label : undefined,
      onClick: props.onClick
    },
      props.loading ? h("span", { className: "tc-spinner", "aria-hidden": "true" }) : (props.icon ? h(Icon, { name: props.icon, size: 20 }) : null),
      props.children ? h("span", null, props.children) : null
    );
  }

  // ---------------------------------------------------------------- TextField
  function TextField(props) {
    var id = useId();
    var helpId = id + "-help";
    var st = useState(props.defaultValue || "");
    var value = props.value !== undefined ? props.value : st[0];
    var hasError = !!props.error;
    return h("div", { className: cx("tc-field", hasError && "has-error", props.disabled && "is-disabled") },
      h("label", { className: "tc-field-label label-md", htmlFor: id }, props.label),
      h("div", { className: "tc-field-box" },
        props.icon ? h(Icon, { name: props.icon, size: 20, className: "tc-field-icon" }) : null,
        h(props.multiline ? "textarea" : "input", {
          id: id, className: "tc-field-input body-lg", value: value, placeholder: props.placeholder,
          disabled: props.disabled, type: props.multiline ? undefined : (props.type || "text"),
          rows: props.multiline ? 3 : undefined,
          "aria-invalid": hasError ? "true" : undefined,
          "aria-describedby": (props.error || props.helper) ? helpId : undefined,
          onChange: function (e) { st[1](e.target.value); if (props.onChange) props.onChange(e.target.value); }
        })
      ),
      (props.error || props.helper) ? h("div", { id: helpId, className: cx("tc-field-help body-sm", hasError && "is-error") },
        hasError ? h(Icon, { name: "error", size: 16 }) : null, h("span", null, props.error || props.helper)) : null
    );
  }

  // ---------------------------------------------------------------- Chip
  function Chip(props) {
    var st = useState(!!props.defaultSelected);
    var selected = props.selected !== undefined ? props.selected : st[0];
    var isFilter = props.kind !== "assist";
    return h("button", {
      type: "button",
      className: cx("tc-chip", "label-md", selected && "is-selected"),
      "aria-pressed": isFilter ? String(selected) : undefined,
      disabled: props.disabled,
      onClick: function () { st[1](!selected); if (props.onChange) props.onChange(!selected); }
    },
      (isFilter && selected) ? h(Icon, { name: "check", size: 16 }) : (props.icon ? h(Icon, { name: props.icon, size: 16 }) : null),
      h("span", null, props.children)
    );
  }

  // ---------------------------------------------------------------- Switch
  function Switch(props) {
    var id = useId();
    var st = useState(!!props.defaultChecked);
    var on = props.checked !== undefined ? props.checked : st[0];
    return h("label", { className: cx("tc-switch-row", props.disabled && "is-disabled"), htmlFor: id },
      props.label ? h("span", { className: "tc-switch-text" },
        h("span", { className: "body-md" }, props.label),
        props.description ? h("span", { className: "body-sm tc-muted" }, props.description) : null) : null,
      h("button", {
        id: id, type: "button", role: "switch", "aria-checked": String(on), disabled: props.disabled,
        className: cx("tc-switch", on && "is-on"),
        onClick: function () { st[1](!on); if (props.onChange) props.onChange(!on); }
      }, h("span", { className: "tc-switch-thumb" }, on ? h(Icon, { name: "check", size: 16 }) : null))
    );
  }

  // ---------------------------------------------------------------- SegmentedControl
  function SegmentedControl(props) {
    var opts = props.options || [];
    var st = useState(props.defaultValue !== undefined ? props.defaultValue : (opts[0] && opts[0].value));
    var value = props.value !== undefined ? props.value : st[0];
    return h("div", { className: "tc-seg", role: "radiogroup", "aria-label": props.label },
      opts.map(function (o) {
        var sel = o.value === value;
        return h("button", {
          key: o.value, type: "button", role: "radio", "aria-checked": String(sel),
          className: cx("tc-seg-item", "label-md", sel && "is-selected"),
          onClick: function () { st[1](o.value); if (props.onChange) props.onChange(o.value); }
        }, o.label);
      })
    );
  }

  // ---------------------------------------------------------------- ListGroup / ListRow
  function ListRow(props) {
    var interactive = !!(props.onClick || props.chevron);
    var Tag = interactive ? "button" : "div";
    return h("li", { className: "tc-row-li" },
      h(Tag, { type: interactive ? "button" : undefined, className: cx("tc-row", interactive && "is-interactive", props.tone === "danger" && "tc-row-danger"), onClick: props.onClick },
        props.icon ? h("span", { className: "tc-row-lead" }, h(Icon, { name: props.icon, size: 24 })) : (props.leading || null),
        h("span", { className: "tc-row-text" },
          h("span", { className: "body-md tc-row-title" }, props.title),
          props.subtitle ? h("span", { className: "body-sm tc-muted" }, props.subtitle) : null),
        props.value ? h("span", { className: "body-md tc-muted tc-row-value" }, props.value) : null,
        props.trailing || null,
        props.chevron ? h(Icon, { name: "chevron", size: 20, className: "tc-row-chev" }) : null
      ));
  }
  function ListGroup(props) {
    return h("section", { className: "tc-group" },
      props.title ? h("h3", { className: "tc-group-title label-md" }, props.title) : null,
      h("ul", { className: "tc-group-cell" }, props.children),
      props.footer ? h("p", { className: "tc-group-footer body-sm" }, props.footer) : null);
  }
  ListGroup.Row = ListRow;

  // ---------------------------------------------------------------- Avatar
  function avatarSlot(seed) {
    var hash = 0;
    for (var i = 0; i < seed.length; i++) hash = (hash * 31 + seed.charCodeAt(i)) | 0;
    return ((hash % 14) + 14) % 14 + 1;
  }
  function Avatar(props) {
    var size = props.size || "md";
    var name = props.name || "?";
    var initials = name.trim().split(/\s+/).slice(0, 2).map(function (w) { return w[0]; }).join("").toUpperCase();
    var slot = avatarSlot(props.seed || name);
    return h("span", {
      className: cx("tc-avatar", "tc-avatar-" + size), role: "img", "aria-label": name,
      style: props.src ? undefined : { background: "var(--avatar-" + slot + ")" }
    }, props.src ? h("img", { src: props.src, alt: "" }) : h("span", { "aria-hidden": "true" }, initials));
  }

  // ---------------------------------------------------------------- Message
  function Message(props) {
    var mine = props.role === "user";
    return h("article", { className: cx("tc-msg", mine ? "tc-msg-user" : "tc-msg-assistant", props.error && "has-error"), "aria-label": (mine ? "You" : (props.sender || "Assistant")) + (props.time ? ", " + props.time : "") },
      !mine ? h("header", { className: "tc-msg-head" },
        h(Avatar, { name: props.sender || "Assistant", size: "sm", seed: props.sender }),
        h("span", { className: "label-md" }, props.sender || "Assistant"),
        props.model ? h("span", { className: "tc-badge label-sm" }, props.model) : null) : null,
      h("div", { className: cx("tc-msg-body", "body-lg", props.streaming && "is-streaming") }, props.children),
      props.error ? h("div", { className: "tc-msg-error body-sm", role: "alert" }, h(Icon, { name: "error", size: 16 }), h("span", null, props.error)) : null,
      (props.time && !props.streaming) ? h("footer", { className: "tc-msg-meta body-sm" }, props.time) : null
    );
  }

  // ---------------------------------------------------------------- Composer
  function Composer(props) {
    var st = useState(props.defaultValue || "");
    var text = st[0];
    var streaming = !!props.streaming;
    var canSend = text.trim().length > 0;
    return h("form", { className: "tc-composer", onSubmit: function (e) { e.preventDefault(); if (canSend && props.onSend) props.onSend(text); st[1](""); } },
      h(Button, { variant: "text", icon: "add", label: "Open tools menu", size: "md" }),
      h("textarea", {
        className: "tc-composer-input body-lg", rows: 1, value: text,
        placeholder: props.placeholder || "Message…", "aria-label": "Message input field",
        onChange: function (e) { st[1](e.target.value); }
      }),
      streaming
        ? h("button", { type: "button", className: "tc-send is-stop", "aria-label": "Stop generation" }, h(Icon, { name: "stop", size: 20 }))
        : (canSend
          ? h("button", { type: "submit", className: "tc-send", "aria-label": "Send message" }, h(Icon, { name: "send", size: 20 }))
          : h("button", { type: "button", className: "tc-send is-quiet", "aria-label": "Start voice recording" }, h(Icon, { name: "mic", size: 20 })))
    );
  }

  // ---------------------------------------------------------------- Banner
  var BANNER_ICON = { danger: "error", warning: "warning", success: "success", info: "info" };
  function Banner(props) {
    var tone = props.tone || "info";
    var st = useState(true);
    if (!st[0]) return null;
    return h("div", { className: cx("tc-banner", "tc-banner-" + tone), role: tone === "danger" ? "alert" : "status" },
      h(Icon, { name: BANNER_ICON[tone], size: 20, className: "tc-banner-icon" }),
      h("div", { className: "tc-banner-text" },
        props.title ? h("strong", { className: "title-sm" }, props.title) : null,
        h("span", { className: "body-sm" }, props.children)),
      props.action ? h("button", { type: "button", className: "tc-banner-action label-md", onClick: props.onAction }, props.action) : null,
      props.dismissible ? h("button", { type: "button", className: "tc-banner-close", "aria-label": "Dismiss", onClick: function () { st[1](false); } }, h(Icon, { name: "close", size: 20 })) : null
    );
  }

  // ---------------------------------------------------------------- Dialog
  function Dialog(props) {
    var titleId = useId();
    return h("div", { className: cx("tc-dialog-scrim", props.inline && "is-inline") },
      h("div", { className: "tc-dialog", role: "alertdialog", "aria-modal": "true", "aria-labelledby": titleId },
        props.icon ? h(Icon, { name: props.icon, size: 24, className: cx("tc-dialog-icon", props.tone === "danger" && "is-danger") }) : null,
        h("h2", { id: titleId, className: "title-lg tc-dialog-title" }, props.title),
        props.children ? h("div", { className: "body-md tc-muted tc-dialog-body" }, props.children) : null,
        h("div", { className: "tc-dialog-actions" },
          h(Button, { variant: "text", onClick: props.onDismiss }, props.dismissLabel || "Cancel"),
          h(Button, { variant: "filled", tone: props.tone, onClick: props.onConfirm }, props.confirmLabel))
      ));
  }

  // ---------------------------------------------------------------- EmptyState
  function EmptyState(props) {
    return h("div", { className: "tc-empty" },
      h("span", { className: "tc-empty-icon" }, h(Icon, { name: props.icon || "chat", size: 24 })),
      h("h2", { className: "title-md" }, props.title),
      props.children ? h("p", { className: "body-md tc-muted" }, props.children) : null,
      props.action ? h(Button, { variant: "tonal", icon: props.actionIcon || "add", onClick: props.onAction }, props.action) : null);
  }

  window.Tchat = {
    Button: Button, TextField: TextField, Chip: Chip, Switch: Switch, SegmentedControl: SegmentedControl,
    ListGroup: ListGroup, ListRow: ListRow, Avatar: Avatar, Message: Message, Composer: Composer,
    Banner: Banner, Dialog: Dialog, EmptyState: EmptyState, Icon: Icon, avatarSlot: avatarSlot
  };
})();
