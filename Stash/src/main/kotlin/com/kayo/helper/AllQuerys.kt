package com.kayo.helper

fun getAllScenes(page: Int, query: String = ""): String {
    return """
{
  "operationName": "FindScenes",
  "variables": {
    "filter": {
      "q": "$query",
      "page": $page,
      "per_page": 40,
      "sort": "date",
      "direction": "DESC"
    },
    "scene_filter": {},
    "scene_ids": null
  },
  "query": "query FindScenes(\${'$'}filter: FindFilterType, \${'$'}scene_filter: SceneFilterType, \${'$'}scene_ids: [Int!]) { findScenes(filter: \${'$'}filter, scene_filter: \${'$'}scene_filter, scene_ids: \${'$'}scene_ids) { count filesize duration scenes { ...SlimSceneData __typename } __typename }} fragment SlimSceneData on Scene { id title date o_counter rating100 files { ...VideoFileData __typename } paths { screenshot preview stream webp sprite __typename } tags { id name __typename } performers { id name image_path __typename } studio { id name image_path __typename } __typename } fragment VideoFileData on VideoFile { id path width height duration video_codec audio_codec size __typename }"
}
""".trimIndent()
}
