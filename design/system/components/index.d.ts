// Tchat components — window.Tchat. Documentation only; previews render these from components/bundle.js.
import type { ReactNode } from "react";

export type IconName =
  | "add" | "check" | "close" | "chevron" | "error" | "warning" | "success" | "info" | "send" | "stop"
  | "mic" | "search" | "chat" | "delete" | "person" | "bell" | "copy" | "contrast";

export interface IconProps {
  name: IconName;
  /** px; 16 in chips, 20 in buttons and fields, 24 standalone. Default 24. */
  size?: 16 | 20 | 24;
  /** Set only when the icon carries meaning on its own; otherwise it is hidden from assistive tech. */
  label?: string;
  className?: string;
}

export interface ButtonProps {
  /** Emphasis: one `filled` per view. Default "filled". */
  variant?: "filled" | "tonal" | "outlined" | "text";
  /** Recolours with `danger` — only for actions that destroy data. */
  tone?: "danger";
  /** "md" = 48px (default), "sm" = 36px with a 48px hit area. */
  size?: "md" | "sm";
  /** Leading 20px icon; with no children the button is icon-only and `label` is required. */
  icon?: IconName;
  /** Accessibility name for an icon-only button. */
  label?: string;
  loading?: boolean;
  disabled?: boolean;
  fullWidth?: boolean;
  type?: "button" | "submit";
  onClick?: () => void;
  /** The label: a sentence-case verb phrase. */
  children?: ReactNode;
}

export interface TextFieldProps {
  /** Always shown above the box. */
  label: string;
  value?: string;
  defaultValue?: string;
  onChange?: (value: string) => void;
  /** An example in `ink-subtle`, never a substitute for the label. */
  placeholder?: string;
  helper?: string;
  /** Replaces `helper`; turns the field `danger`. Say how to fix it. */
  error?: string;
  icon?: IconName;
  type?: string;
  multiline?: boolean;
  disabled?: boolean;
}

export interface ChipProps extends ChipRemoveProps {
  /** "filter" toggles (default); "assist" runs an action. */
  kind?: "filter" | "assist";
  selected?: boolean;
  defaultSelected?: boolean;
  onChange?: (selected: boolean) => void;
  icon?: IconName;
  disabled?: boolean;
  children: ReactNode;
}

export interface SwitchProps {
  label?: string;
  description?: string;
  checked?: boolean;
  defaultChecked?: boolean;
  onChange?: (checked: boolean) => void;
  disabled?: boolean;
}

export interface SegmentedControlProps {
  /** 2–4 options, one or two words each. */
  options: { value: string; label: string }[];
  value?: string;
  defaultValue?: string;
  onChange?: (value: string) => void;
  /** Accessibility name of the radio group. */
  label?: string;
}

export interface ListRowProps {
  title: ReactNode;
  subtitle?: ReactNode;
  icon?: IconName;
  /** Custom leading content, e.g. an Avatar. */
  leading?: ReactNode;
  /** The current choice, shown trailing in `ink-muted`. */
  value?: ReactNode;
  /** Trailing control, e.g. a Switch. */
  trailing?: ReactNode;
  /** The row opens another page. */
  chevron?: boolean;
  tone?: "danger";
  onClick?: () => void;
}

export interface ListGroupProps {
  title?: string;
  footer?: string;
  children: ReactNode;
}

export interface AvatarProps {
  /** Accessibility name and source of the initials. */
  name: string;
  src?: string;
  /** Stable id that picks `avatar-1`…`avatar-14`; defaults to `name`. */
  seed?: string;
  /** sm 28px, md 40px (default), lg 64px. */
  size?: "sm" | "md" | "lg";
}

export interface MessageProps {
  role: "user" | "assistant";
  /** Assistant display name (agent or app). */
  sender?: string;
  /** Model or endpoint badge. */
  model?: string;
  time?: string;
  streaming?: boolean;
  /** Shown under whatever was received. */
  error?: string;
  /** Rendered markdown. */
  children: ReactNode;
}

export interface ComposerProps extends ComposerAboveProps {
  placeholder?: string;
  defaultValue?: string;
  /** A reply is streaming: the action becomes Stop. */
  streaming?: boolean;
  onSend?: (text: string) => void;
}

export interface BannerProps {
  tone?: "info" | "success" | "warning" | "danger";
  title?: string;
  /** One or two sentences: what happened, then what to do. */
  children: ReactNode;
  action?: string;
  onAction?: () => void;
  dismissible?: boolean;
}

export interface DialogProps {
  /** A question. */
  title: string;
  /** The consequence, in one or two sentences. */
  children?: ReactNode;
  /** A verb: "Delete", "Discard". */
  confirmLabel: string;
  dismissLabel?: string;
  icon?: IconName;
  tone?: "danger";
  onConfirm?: () => void;
  onDismiss?: () => void;
  /** Render in flow instead of fixed over the viewport (previews, docs). */
  inline?: boolean;
}

export interface EmptyStateProps {
  title: string;
  children?: ReactNode;
  icon?: IconName;
  action?: string;
  actionIcon?: IconName;
  onAction?: () => void;
}

export declare function Button(props: ButtonProps): JSX.Element;
export declare function TextField(props: TextFieldProps): JSX.Element;
export declare function Chip(props: ChipProps): JSX.Element;
export declare function Switch(props: SwitchProps): JSX.Element;
export declare function SegmentedControl(props: SegmentedControlProps): JSX.Element;
export declare const ListGroup: ((props: ListGroupProps) => JSX.Element) & { Row: (props: ListRowProps) => JSX.Element };
export declare function ListRow(props: ListRowProps): JSX.Element;
export declare function Avatar(props: AvatarProps): JSX.Element;
export declare function Message(props: MessageProps): JSX.Element;
export declare function Composer(props: ComposerProps): JSX.Element;
export declare function Banner(props: BannerProps): JSX.Element | null;
export declare function Dialog(props: DialogProps): JSX.Element;
export declare function EmptyState(props: EmptyStateProps): JSX.Element;
export declare function Icon(props: IconProps): JSX.Element;
/** 1-based avatar slot for a seed — the same hash as `avatarColorForSeed` in :core:ui. */
export declare function avatarSlot(seed: string): number;

export interface ChipRemoveProps {
  /** Adds a trailing remove button (an input chip), e.g. the composer's Image Gen chip. */
  onRemove?: () => void;
  /** Accessibility name of the remove button. Default "Remove". */
  removeLabel?: string;
  /** Overrides toggling: the chip runs this action instead (e.g. open the picker on Image Gen). */
  onClick?: () => void;
}

export interface ComposerAboveProps {
  /** Chips stacked above the field: attachments, tool toggles, the Image Gen chip. */
  above?: ReactNode;
}

export interface ModelTriggerProps {
  /** Readable model name ("GPT-6 Luna"), never the raw id. */
  label: string;
  /** The maker ("OpenAI"), or "Agent". */
  owner: string;
  /** The maker's art; previews use a 1–2 letter monogram. */
  mark?: string;
  free?: boolean;
  /** Label of the Image Gen model when Image Gen is on. */
  imageModel?: string;
  onClick?: () => void;
}

export interface ModelPickerRow {
  /** Chat: the model id. Image: the imageList entry name. Agent: the agent id. */
  id: string;
  label: string;
  /** Image rows: the model id shown under the label. */
  model?: string;
  /** Agent rows: one line of description. */
  detail?: string;
  /** From /api/tchat/models/catalog. */
  free?: boolean;
  pinned?: boolean;
}

export interface ModelPickerGroup {
  id: string;
  label: string;
  /** Model family where it differs from the maker ("Claude"). */
  caption?: string;
  mark?: string;
  kind?: "image" | "agent";
  /** Image group: the one-line explanation of how Image Gen works. */
  intro?: string;
  rows: ModelPickerRow[];
}

export interface ModelPickerProps {
  /** Makers in display order, then Image Gen, then Agents. */
  groups: ModelPickerGroup[];
  /** Current chat model id. */
  selected?: string;
  /** Active Image Gen entry, or null when Image Gen is off. */
  image?: string | null;
  /** Initial maker filter ("all" by default). */
  filter?: string;
  search?: string;
  onSelect?: (modelId: string) => void;
  /** null turns Image Gen off. */
  onImage?: (imageSpec: string | null) => void;
}

export declare function ModelTrigger(props: ModelTriggerProps): JSX.Element;
export declare function ModelPicker(props: ModelPickerProps): JSX.Element;
