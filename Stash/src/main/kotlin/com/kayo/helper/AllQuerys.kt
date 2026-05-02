package com.kayo.helper

fun getAllScenes(page: Int, query: String = ""): String {
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

fun getUpdatedAtScenes(page: Int, query: String = ""): String {
    return """{
  "operationName": "FindScenes",
  "variables": {
    "filter": {
      "q": "$query",
      "page": $page,
      "per_page": 40,
      "sort": "updated_at",
      "direction": "DESC"
    },
    "scene_filter": {},
    "scene_ids": null
  },
  "query": "query FindScenes(${'$'}filter: FindFilterType, ${'$'}scene_filter: SceneFilterType, ${'$'}scene_ids: [Int!]) {\n  findScenes(filter: ${'$'}filter, scene_filter: ${'$'}scene_filter, scene_ids: ${'$'}scene_ids) {\n    count\n    filesize\n    duration\n    scenes {\n      ...SlimSceneData\n      __typename\n    }\n    __typename\n  }\n}\n\nfragment SlimSceneData on Scene {\n  id\n  title\n  date\n  o_counter\n  rating100\n  files {\n    ...VideoFileData\n    __typename\n  }\n  paths {\n    screenshot\n    preview\n    stream\n    webp\n    sprite\n    __typename\n  }\n  tags { id name __typename }\n  performers { id name image_path __typename }\n  studio { id name image_path __typename }\n  __typename\n}\n\nfragment VideoFileData on VideoFile {\n  id\n  path\n  width\n  height\n  duration\n  video_codec\n  audio_codec\n  size\n  __typename\n}"
}
""".trimIndent()
}

fun findSceneById(id: Int): String {
    return """{
  "operationName": "FindScene",
  "variables": {
    "id": $id
  },
  "query": "query FindScene(${'$'}id: ID!) {\n  findScene(id: ${'$'}id) {\n    id\n    title\n    code\n    details\n    urls\n    date\n    rating100\n    o_counter\n    organized\n    interactive\n    interactive_speed\n    captions { language_code caption_type }\n    created_at\n    updated_at\n    resume_time\n    last_played_at\n    play_duration\n    play_count\n    play_history\n    o_history\n    files {\n      id\n      path\n      size\n      mod_time\n      duration\n      video_codec\n      audio_codec\n      width\n      height\n      frame_rate\n      bit_rate\n      fingerprints { type value }\n    }\n    paths {\n      screenshot\n      preview\n      stream\n      webp\n      vtt\n      sprite\n      funscript\n      interactive_heatmap\n      caption\n    }\n    scene_markers {\n      id\n      title\n      seconds\n      end_seconds\n      stream\n      preview\n      screenshot\n      primary_tag { id name }\n      tags { id name }\n    }\n    galleries {\n      id\n      title\n      image_count\n      paths { cover preview }\n    }\n    studio {\n      id\n      name\n      image_path\n    }\n    performers {\n      id\n      name\n      disambiguation\n      gender\n      favorite\n      image_path\n      rating100\n    }\n    tags { id name }\n    stash_ids { endpoint stash_id updated_at }\n    sceneStreams { url mime_type label }\n  }\n}"
}
"""
}