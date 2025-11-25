package com.kayo.helper

data class FindScenesResponse(
    val data: FindScenesData?
)

data class FindScenesData(
    val findScenes: FindScenesResult?
)

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
    val files: List<VideoFile>?,
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

data class VideoFile(
    val id: String?,
    val path: String?,
    val width: Int?,
    val height: Int?,
    val duration: Double?,
    val video_codec: String?,
    val audio_codec: String?,
    val size: Long?
)

data class Tag(
    val id: String?,
    val name: String?
)

data class Performer(
    val id: String?,
    val name: String?,
    val image_path: String?
)

data class Studio(
    val id: String?,
    val name: String?,
    val image_path: String?
)
