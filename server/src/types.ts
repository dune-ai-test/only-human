/** Shared types for the world server. Mirrors the app's models. */

export interface Env {
  DB: D1Database;
  DEFAULT_ENDPOINT: string;
  DEFAULT_MODEL: string;
}

export interface WorldRow {
  id: string;
  created_at: number;
  utc_offset: number;
  me_name: string;
  me_handle: string;
  me_avatar: number;
  next_tick_at: number;
  quiet_until: number;
  calls_today: number;
  calls_day: string;
  daily_budget: number;
  last_contact_at: number;
  endpoint: string | null;
  api_key: string | null;
  model: string | null;
}

export interface PersonaRow {
  world_id: string;
  id: string;
  name: string;
  handle: string;
  bio: string;
  occupation: string;
  location: string;
  voice: string;
  gradient: string;
  joined_year: string;
  follower_boost: number;
  model: string | null;
  custom: number;
}

export interface RelationRow {
  world_id: string;
  a: string;
  b: string;
  label: string;
}

export interface PostRow {
  world_id: string;
  id: string;
  author_id: string;
  text: string;
  created_at: number;
  likes: number;
  replies: number;
  liked_by_me: number;
}

export interface ReplyRow {
  world_id: string;
  id: string;
  post_id: string;
  author_id: string;
  text: string;
  created_at: number;
}

export interface StoryRow {
  world_id: string;
  id: string;
  persona_id: string;
  text: string;
  created_at: number;
}

export interface DmRow {
  world_id: string;
  id: string;
  persona_id: string;
  from_me: number;
  text: string;
  at: number;
}

export interface AskRow {
  world_id: string;
  id: string;
  post_id: string;
  persona_id: string;
  at: number;
  state: string;
}

export interface FollowRow {
  world_id: string;
  persona_id: string;
  following: number;
  followed_by: number;
}

/** A note the human leaves about the world — news, weather, a small fact. */
export interface NoteRow {
  id: string;
  text: string;
  at: number;
}

export interface ActivityRow {
  world_id: string;
  id: string;
  persona_id: string;
  bold_part: string;
  rest: string;
  kind: string;
  created_at: number;
}

export interface JobRow {
  id: string;
  world_id: string;
  run_at: number;
  kind: string;
  payload: string;
  done: number;
  created_at: number;
}
