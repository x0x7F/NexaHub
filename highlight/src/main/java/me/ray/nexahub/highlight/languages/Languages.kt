package me.ray.nexahub.highlight.languages

import me.ray.nexahub.highlight.core.Language
import me.ray.nexahub.highlight.languages.bash.bash
import me.ray.nexahub.highlight.languages.c.c
import me.ray.nexahub.highlight.languages.cmake.cmake
import me.ray.nexahub.highlight.languages.cpp.cpp
import me.ray.nexahub.highlight.languages.csharp.csharp
import me.ray.nexahub.highlight.languages.css.css
import me.ray.nexahub.highlight.languages.dart.dart
import me.ray.nexahub.highlight.languages.diff.diff
import me.ray.nexahub.highlight.languages.dockerfile.dockerfile
import me.ray.nexahub.highlight.languages.go.go
import me.ray.nexahub.highlight.languages.glsl.glsl
import me.ray.nexahub.highlight.languages.ini.ini
import me.ray.nexahub.highlight.languages.java.java
import me.ray.nexahub.highlight.languages.javascript.javascript
import me.ray.nexahub.highlight.languages.json.json
import me.ray.nexahub.highlight.languages.kotlin.kotlin
import me.ray.nexahub.highlight.languages.latex.latex
import me.ray.nexahub.highlight.languages.lua.lua
import me.ray.nexahub.highlight.languages.markdown.markdown
import me.ray.nexahub.highlight.languages.php.php
import me.ray.nexahub.highlight.languages.powershell.powershell
import me.ray.nexahub.highlight.languages.properties.properties
import me.ray.nexahub.highlight.languages.python.python
import me.ray.nexahub.highlight.languages.rust.rust
import me.ray.nexahub.highlight.languages.ruby.ruby
import me.ray.nexahub.highlight.languages.sql.sql
import me.ray.nexahub.highlight.languages.swift.swift
import me.ray.nexahub.highlight.languages.typescript.typescript
import me.ray.nexahub.highlight.languages.xml.xml
import me.ray.nexahub.highlight.languages.yaml.yaml

/**
 * Every grammar bundled with the highlighter.
 *
 * Each entry builds a fresh mode tree: compilation mutates modes in place, mirroring `highlight.js`.
 */
internal fun builtinLanguages(): List<Language> = listOf(
    json(),
    ini(),
    cmake(),
    go(),
    glsl(),
    yaml(),
    bash(),
    dockerfile(),
    javascript(),
    typescript(),
    xml(),
    css(),
    dart(),
    java(),
    kotlin(),
    latex(),
    lua(),
    powershell(),
    properties(),
    python(),
    c(),
    cpp(),
    csharp(),
    sql(),
    diff(),
    markdown(),
    rust(),
    ruby(),
    php(),
    swift(),
)
