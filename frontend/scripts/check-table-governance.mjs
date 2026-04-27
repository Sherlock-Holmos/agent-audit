import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative, resolve } from 'node:path'

const rootDir = resolve(process.cwd(), 'src')
const allowList = new Set([
  'components/shared/AppDataTable.vue'
])

const rootTagPattern = /<el-table(\s|>)/g

function walkVueFiles(dir, bucket) {
  const entries = readdirSync(dir)
  for (const entry of entries) {
    const fullPath = join(dir, entry)
    const stats = statSync(fullPath)
    if (stats.isDirectory()) {
      walkVueFiles(fullPath, bucket)
      continue
    }
    if (entry.endsWith('.vue')) {
      bucket.push(fullPath)
    }
  }
}

function collectViolations() {
  const files = []
  walkVueFiles(rootDir, files)

  const violations = []
  for (const filePath of files) {
    const relPath = relative(rootDir, filePath).replace(/\\/g, '/')
    if (allowList.has(relPath)) {
      continue
    }

    const content = readFileSync(filePath, 'utf8')
    if (rootTagPattern.test(content)) {
      violations.push(relPath)
    }
    rootTagPattern.lastIndex = 0
  }

  return violations
}

try {
  const violations = collectViolations()
  if (!violations.length) {
    console.log('Table governance check passed: no raw <el-table> roots outside AppDataTable.')
    process.exit(0)
  }

  console.error('Table governance check failed. Found raw <el-table> root tags in:')
  for (const file of violations) {
    console.error(`- src/${file}`)
  }
  console.error('Please migrate these tables to AppDataTable.')
  process.exit(1)
} catch (error) {
  console.error('Table governance check crashed:', error?.message || error)
  process.exit(2)
}
