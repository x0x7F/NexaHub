import type { UIMessage } from "./message";

/**
 * Message node - container for message branching
 * @see app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt - MessageNode
 */
export interface MessageNode {
  id: string;
  messages: UIMessage[];
  selectIndex: number;
}

/**
 * Conversation
 * @see app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt - Conversation
 */
export interface Conversation {
  id: string;
  assistantId: string;
  title: string;
  messageNodes: MessageNode[];
  chatSuggestions: string[];
  isPinned: boolean;
  customSystemPrompt?: string | null;
  modeInjectionIds?: string[];
  lorebookIds?: string[];
  /** Absolute path inside the workspace rootfs */
  workspaceCwd?: string | null;
  /** Folder it belongs to (grouping within an assistant); null means not in any folder */
  folderId?: string | null;
  createAt: number;
  updateAt: number;
}
