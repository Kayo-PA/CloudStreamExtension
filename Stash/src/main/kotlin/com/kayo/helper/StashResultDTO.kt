package com.kayo.helper

// =====================
// ROOT RESPONSES
// =====================

data class FindScenesResponse(
    val data: FindScenesData?
)

data class FindScenesData(
    val findScenes: FindScenesResult?
)

data class FindSceneResponse(
    val data: FindSceneData?
)

data class FindSceneData(
    val findScene: SceneFull?
)


// =====================
// FIND SCENES (LIST)
// =====================

data class FindScenesResult(
    val count: Int?,
    val filesize: Double?,
    val duration: Double?,
    val scenes: List<SceneItem>?
)

data class SceneItem(
    val id: String?,
    val title: String?,
    val date: String?,
    val o_counter: Int?,
    val rating100: Int?,
    val files: List<VideoFileFull>?,
    val paths: ScenePaths?,
    val tags: List<Tag>?,
    val performers: List<Performer>?,
    val studio: Studio?
)

data class ScenePaths(
    val screenshot: String?,
    val preview: String?,
    val stream: String?,
    val webp: String?,
    val sprite: String?
)


// =====================
// FIND SCENE (FULL DETAILS)
// =====================

data class SceneFull(
    val id: String?,
    val title: String?,
    val code: String?,
    val details: String?,
    val urls: List<String>?,
    val date: String?,
    val rating100: Int?,
    val o_counter: Int?,
    val organized: Boolean?,
    val interactive: Boolean?,
    val interactive_speed: Int?,
    val captions: List<Caption>?,
    val created_at: String?,
    val updated_at: String?,
    val resume_time: Double?,
    val last_played_at: String?,
    val play_duration: Double?,
    val play_count: Int?,
    val play_history: List<String>?,
    val o_history: List<String>?,
    val files: List<VideoFileFull>?,
    val paths: ScenePathsFull?,
    val scene_markers: List<SceneMarker>?,
    val galleries: List<GallerySlim>?,
    val studio: Studio?,
    val performers: List<PerformerFull>?,
    val tags: List<Tag>?,
    val stash_ids: List<StashID>?,
    val sceneStreams: List<SceneStream>?
)


// =====================
// FULL FILE INFO
// =====================

data class VideoFileFull(
    val id: String?,
    val path: String?,
    val size: Long?,
    val mod_time: String?,
    val duration: Double?,
    val video_codec: String?,
    val audio_codec: String?,
    val width: Int?,
    val height: Int?,
    val frame_rate: Double?,
    val bit_rate: Long?,
    val fingerprints: List<Fingerprint>?
)

data class Fingerprint(
    val type: String?,
    val value: String?
)


// =====================
// FULL PATHS INFO
// =====================

data class ScenePathsFull(
    val screenshot: String?,
    val preview: String?,
    val stream: String?,
    val webp: String?,
    val vtt: String?,
    val sprite: String?,
    val funscript: String?,
    val interactive_heatmap: String?,
    val caption: String?
)


// =====================
// CAPTIONS
// =====================

data class Caption(
    val language_code: String?,
    val caption_type: String?
)


// =====================
// SCENE MARKERS
// =====================

data class SceneMarker(
    val id: String?,
    val title: String?,
    val seconds: Double?,
    val end_seconds: Double?,
    val stream: String?,
    val preview: String?,
    val screenshot: String?,
    val primary_tag: Tag?,
    val tags: List<Tag>?
)


// =====================
// GALLERIES
// =====================

data class GallerySlim(
    val id: String?,
    val title: String?,
    val image_count: Int?,
    val paths: GalleryPaths?
)

data class GalleryPaths(
    val cover: String?,
    val preview: String?
)


// =====================
// PERFORMERS
// =====================

data class Performer(
    val id: String?,
    val name: String?,
    val image_path: String?
)

data class PerformerFull(
    val id: String?,
    val name: String?,
    val disambiguation: String?,
    val gender: String?,
    val favorite: Boolean?,
    val image_path: String?,
    val rating100: Int?
)


// =====================
// STUDIO
// =====================

data class Studio(
    val id: String?,
    val name: String?,
    val image_path: String?
)


// =====================
// TAG
// =====================

data class Tag(
    val id: String?,
    val name: String?
)


// =====================
// STASH ID
// =====================

data class StashID(
    val endpoint: String?,
    val stash_id: String?,
    val updated_at: String?
)


// =====================
// SCENE STREAMS
// =====================

data class SceneStream(
    val url: String?,
    val mime_type: String?,
    val label: String?
)
