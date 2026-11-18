import { afterEach, describe, expect, it } from 'vitest'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { createRequire } from 'node:module'
import { patchPicomatch } from '../patch-picomatch.mjs'

const require = createRequire(import.meta.url)
const modulePath = require.resolve('picomatch')
const packageRoot = dirname(modulePath)
const temporaryRoots = []

afterEach(() => {
    for (const root of temporaryRoots.splice(0)) rmSync(root, { recursive: true, force: true })
})

function fixture() {
    const root = mkdtempSync(join(tmpdir(), 'noviis-picomatch-security-'))
    temporaryRoots.push(root)
    const directory = join(root, 'node_modules/picomatch')
    mkdirSync(join(directory, 'lib'), { recursive: true })
    writeFileSync(join(root, 'package-lock.json'), JSON.stringify({
        packages: { 'node_modules/picomatch': { version: '4.0.7' } },
    }), 'utf8')
    writeFileSync(join(directory, 'package.json'), JSON.stringify({ version: '4.0.7' }), 'utf8')
    writeFileSync(join(directory, 'lib/parse.js'), readFileSync(join(packageRoot, 'lib/parse.js')), 'utf8')
    return { root, directory }
}

describe('temporary picomatch security patch', () => {
    it('bounds malicious repeated extglobs in an isolated process', () => {
        // A regression must time out a child process, never hang the test runner.
        const result = spawnSync(process.execPath, ['-e', `
            const assert = require('node:assert/strict');
            const match = require(${JSON.stringify(modulePath)});
            const input = 'a'.repeat(200) + 'b';
            for (const pattern of ['+(*a)', '*(*a)', '+(*a)c', 'x+(*a)']) {
                assert.equal(match.isMatch(input, pattern), false);
            }
            assert.equal(match.isMatch('abc', '+(*)c'), true);
            assert.equal(match.isMatch('axbayb', '+(a*b)'), true);
            assert.equal(match.isMatch('axbay', '+(a*b)'), false);
            assert.equal(match.isMatch('foo.js', '*(*.js)'), true);
            assert.equal(match.isMatch('foo.ts', '*(*.js)'), false);
            assert.equal(match.isMatch('src/a.ts', 'src/**/*.ts'), true);
            assert.equal(match.isMatch('src/a.js', 'src/**/*.ts'), false);
        `], { timeout: 3000, encoding: 'utf8', windowsHide: true })
        expect(result.error, result.stderr).toBeUndefined()
        expect(result.status, result.stderr).toBe(0)
    })

    it('preserves quoted and POSIX-class literals while rejecting ambiguous repeats', () => {
        const result = spawnSync(process.execPath, ['-e', String.raw`
            const assert = require('node:assert/strict');
            const match = require(${JSON.stringify(modulePath)});
            for (const operator of ['+', '*']) {
                assert.equal(match.isMatch('*a*a', operator + '("*a")'), true);
                assert.equal(match.isMatch('a*a*', operator + '("a*")'), true);
                assert.equal(match.isMatch('ab*', operator + '([[:alpha:]*])'), true);
                assert.equal(match.isMatch('ab1*', operator + '([[:alnum:]*])'), true);
                assert.equal(match.isMatch('*ax*ay', operator + '("*"a*)'), true);
            }
            const slash = String.fromCharCode(92);
            assert.equal(match.isMatch('a.bca.bd', '+(a' + slash + '.b*)'), true);
            assert.equal(match.isMatch('axbayb', '+(a*b)', { windows: true }), true);
            const adversarial = '*' + 'a'.repeat(200) + 'b';
            assert.equal(match.isMatch(adversarial, '+("*"*a)'), false);
            assert.equal(match.isMatch('a'.repeat(200) + 'b', '+([[:alpha:]]*a)'), false);
            // Separator-bearing repeats cannot safely be collapsed into one
            // branch. Follow Picomatch's existing risky-extglob literalization.
            for (const pattern of ['+(a' + slash + '/*b)', '+("a/"*b)', '+([[:punct:]]*a)', '+([[:ascii:]]*a)']) {
                assert.equal(match.isMatch('a/ba/b', pattern), false);
                assert.equal(match.isMatch('/a/a', pattern), false);
            }
            assert.equal(match.isMatch('a' + slash + 'ba' + slash + 'b', '+(a' + slash + slash + '*b)', { windows: true }), false);
        `], { timeout: 3000, encoding: 'utf8', windowsHide: true })
        expect(result.error, result.stderr).toBeUndefined()
        expect(result.status, result.stderr).toBe(0)
    })

    it('verifies the installed patch and is idempotent', () => {
        const { root, directory } = fixture()
        const before = readFileSync(join(directory, 'lib/parse.js'), 'utf8')
        expect(patchPicomatch(root, { checkOnly: true })).toBe(1)
        expect(patchPicomatch(root)).toBe(1)
        expect(readFileSync(join(directory, 'lib/parse.js'), 'utf8')).toBe(before)
    })

    it('rejects altered sources without overwriting them', () => {
        const { root, directory } = fixture()
        const path = join(directory, 'lib/parse.js')
        const changed = readFileSync(path, 'utf8') + '\n// unexpected source change\n'
        writeFileSync(path, changed, 'utf8')
        expect(() => patchPicomatch(root)).toThrow('Unrecognized picomatch source')
        expect(readFileSync(path, 'utf8')).toBe(changed)
    })

    it('requires review before applying to a different locked version', () => {
        const { root } = fixture()
        writeFileSync(join(root, 'package-lock.json'), JSON.stringify({
            packages: { 'node_modules/picomatch': { version: '4.0.8' } },
        }), 'utf8')
        expect(() => patchPicomatch(root)).toThrow('Review picomatch security patch')
    })
})
