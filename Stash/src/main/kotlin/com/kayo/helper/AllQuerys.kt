package com.kayo.helper

fun getAllScenes(page: Int, query: String = ""):String{
    return """{
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
  "query": "query FindScenes(${'$'}filter: FindFilterType, ${'$'}scene_filter: SceneFilterType, ${'$'}scene_ids: [Int!]) {\n  findScenes(filter: ${'$'}filter, scene_filter: ${'$'}scene_filter, scene_ids: ${'$'}scene_ids) {\n    count\n    filesize\n    duration\n    scenes {\n      ...SlimSceneData\n      __typename\n    }\n    __typename\n  }\n}\n\nfragment SlimSceneData on Scene {\n  id\n  title\n  date\n  o_counter\n  rating100\n  files {\n    ...VideoFileData\n    __typename\n  }\n  paths {\n    screenshot\n    preview\n    stream\n    webp\n    sprite\n    __typename\n  }\n  tags { id name __typename }\n  performers { id name image_path __typename }\n  studio { id name image_path __typename }\n  __typename\n}\n\nfragment VideoFileData on VideoFile {\n  id\n  path\n  width\n  height\n  duration\n  video_codec\n  audio_codec\n  size\n  __typename\n}"
}
""".trimIndent()
}