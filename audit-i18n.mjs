import { messages } from './frontend/src/locales/messages.js';
import fs from 'fs';
import path from 'path';

// Flatten keys
function flattenKeys(obj, prefix = '') {
    let keys = [];
    for (const key in obj) {
        if (typeof obj[key] === 'object' && obj[key] !== null) {
            keys = keys.concat(flattenKeys(obj[key], prefix + key + '.'));
        } else {
            keys.push(prefix + key);
        }
    }
    return keys;
}

const definedKeys = new Set(flattenKeys(messages.ko));

// Scan files
function scanFiles(dir) {
    let results = [];
    const list = fs.readdirSync(dir);
    list.forEach(file => {
        file = path.resolve(dir, file);
        const stat = fs.statSync(file);
        if (stat && stat.isDirectory()) {
            results = results.concat(scanFiles(file));
        } else {
            if (file.endsWith('.vue') || file.endsWith('.js') || file.endsWith('.ts')) {
                const content = fs.readFileSync(file, 'utf8');
                // Regex to match t('key') or $t('key')
                const regex = /(?:\$|(?<![a-zA-Z0-9]))t\s*\(\s*['"]([^'"]+)['"]/g;
                let match;
                while ((match = regex.exec(content)) !== null) {
                    results.push({ key: match[1], file: file });
                }
            }
        }
    });
    return results;
}

const usedKeys = scanFiles('./frontend/src');
const missingKeys = new Set();

usedKeys.forEach(({ key, file }) => {
    if (!definedKeys.has(key)) {
        // Ignore dynamic keys (containing ${} or +) and likely variables
        if (!key.includes('${') && !key.includes('+') && !key.includes(' ')) {
            missingKeys.add(key);
        }
    }
});

console.log('Missing Keys:');
missingKeys.forEach(key => console.log(key));
