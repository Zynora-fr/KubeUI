#!/usr/bin/env node
// Scaffolding script (Phase 224) - copies templates/starter/ to a target directory, so starting a
// new KubeUI-based project doesn't mean copy-pasting a demo script by hand. Plain Node/fs, no
// dependencies, no npm registry package published for it - run locally from this repo:
//
//   node scripts/create-kubeui-script.js <target-dir>

const fs = require('fs');
const path = require('path');

const targetDir = process.argv[2];
if (!targetDir) {
	console.error('Usage: node scripts/create-kubeui-script.js <target-dir>');
	process.exit(1);
}

const templateDir = path.join(__dirname, '..', 'templates', 'starter');

function copyRecursive(src, dest) {
	const stat = fs.statSync(src);
	if (stat.isDirectory()) {
		fs.mkdirSync(dest, {recursive: true});
		for (const entry of fs.readdirSync(src)) {
			copyRecursive(path.join(src, entry), path.join(dest, entry));
		}
		return;
	}

	if (fs.existsSync(dest)) {
		console.error(`Refusing to overwrite existing file: ${dest}`);
		process.exit(1);
	}
	fs.copyFileSync(src, dest);
}

copyRecursive(templateDir, targetDir);
console.log(`Created a KubeUI starter project at ${targetDir}`);
