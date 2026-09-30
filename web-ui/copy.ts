import {
  copyFileSync,
  mkdirSync,
  readdirSync,
  rmSync,
  statSync,
} from "node:fs";
import { join, dirname } from "node:path";

const SOURCE_DIR = "./build/client";
const TARGET_DIR = "../web/src/main/resources/static";

function copyDirectory(src: string, dest: string) {
  // Make sure the target directory exists
  mkdirSync(dest, { recursive: true });

  const entries = readdirSync(src, { withFileTypes: true });

  for (const entry of entries) {
    const srcPath = join(src, entry.name);
    const destPath = join(dest, entry.name);

    if (entry.isDirectory()) {
      copyDirectory(srcPath, destPath);
    } else {
      // Make sure the parent directory exists
      mkdirSync(dirname(destPath), { recursive: true });
      copyFileSync(srcPath, destPath);
    }
  }
}

try {
  console.log("📦 Starting build output copy...");
  console.log(`   Source: ${SOURCE_DIR}`);
  console.log(`   Target: ${TARGET_DIR}`);

  // Check that the source directory exists
  try {
    statSync(SOURCE_DIR);
  } catch {
    console.error(`❌ Source directory not found: ${SOURCE_DIR}`);
    console.error("   Please run build first.");
    process.exit(1);
  }

  // Clean the target directory (if it exists)
  try {
    rmSync(TARGET_DIR, { recursive: true, force: true });
    console.log("🧹 Cleaned target directory");
  } catch (err) {
    // The target directory doesn't exist; ignore the error
  }

  // Copy the files
  copyDirectory(SOURCE_DIR, TARGET_DIR);

  console.log("✅ Build output copied successfully!");
} catch (error) {
  console.error("❌ Copy failed:", error);
  process.exit(1);
}
