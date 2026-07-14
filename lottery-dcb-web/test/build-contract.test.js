import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const servicePom = readFileSync(new URL('../../lottery-dcb-service/pom.xml', import.meta.url), 'utf8')
const application = readFileSync(new URL('../../lottery-dcb-service/src/main/resources/application.yml', import.meta.url), 'utf8')
const applicationMain = readFileSync(new URL('../../lottery-dcb-service/src/main/java/cn/lotterydcb/LotteryDcbApplication.java', import.meta.url), 'utf8')
const consoleConfigurer = readFileSync(new URL('../../lottery-dcb-service/src/main/java/cn/lotterydcb/config/ConsoleCharsetConfigurer.java', import.meta.url), 'utf8')
const frontendController = readFileSync(new URL('../../lottery-dcb-service/src/main/java/cn/lotterydcb/web/FrontendController.java', import.meta.url), 'utf8')
const vite = readFileSync(new URL('../vite.config.js', import.meta.url), 'utf8')

test('Maven replaces static resources immediately before packaging', () => {
  assert.match(servicePom, /<artifactId>maven-antrun-plugin<\/artifactId>/)
  assert.match(servicePom, /<phase>prepare-package<\/phase>/)
  assert.match(servicePom, /<delete dir="\$\{project\.build\.outputDirectory\}\/static"/)
  assert.match(servicePom, /<exclude>static\/\*\*<\/exclude>/)
  assert.match(servicePom, /<directory>\$\{project\.basedir\}\/\.\.\/lottery-dcb-web\/dist<\/directory>/)
  assert.match(servicePom, /<targetPath>static<\/targetPath>/)
  assert.match(servicePom, /substring="前端尚未编译"/)
})

test('IDEA startup can serve the built frontend from either working directory', () => {
  assert.match(application, /file:\.\/lottery-dcb-web\/dist\//)
  assert.match(application, /file:\.\.\/lottery-dcb-web\/dist\//)
  assert.match(application, /use-last-modified: false/)
  assert.match(frontendController, /@GetMapping\("\/"\)/)
  assert.match(frontendController, /return "forward:\/index\.html"/)
})

test('Maven package replaces the root executable jar after Spring Boot repackaging', () => {
  const repackagePosition = servicePom.indexOf('<artifactId>spring-boot-maven-plugin</artifactId>')
  const copyPosition = servicePom.indexOf('<id>copy-executable-jar-to-project-root</id>')
  assert.ok(repackagePosition >= 0 && copyPosition > repackagePosition)
  assert.match(servicePom, /<phase>package<\/phase>/)
  assert.match(servicePom, /jar-verification\/BOOT-INF\/classes\/static\/index\.html/)
  assert.match(servicePom, /jar-verification\/BOOT-INF\/lib/)
  assert.match(servicePom, /<delete file="\$\{project\.basedir\}\/\.\.\/lottery-dcb\.jar"/)
  assert.match(servicePom, /tofile="\$\{project\.basedir\}\/\.\.\/lottery-dcb\.jar"/)
  assert.match(servicePom, /algorithm="SHA-256"/)
  assert.match(servicePom, /lottery-dcb-web\/dist/)
})

test('every frontend build receives a visible build marker', () => {
  assert.match(vite, /__APP_BUILD_TIME__/)
  assert.match(vite, /new Date\(\)\.toISOString\(\)/)
})

test('direct java jar startup detects the terminal charset before logging starts', () => {
  assert.match(application, /console: \$\{CONSOLE_LOG_CHARSET:UTF-8}/)
  assert.match(application, /file: UTF-8/)
  assert.match(applicationMain, /ConsoleCharsetConfigurer\.configure\(\)/)
  assert.match(applicationMain, /launchMode/)
  assert.match(consoleConfigurer, /System\.console\(\)/)
  assert.match(consoleConfigurer, /sun\.stdout\.encoding/)
  assert.match(consoleConfigurer, /native\.encoding/)
  assert.match(consoleConfigurer, /isIdeaProcess\(\)/)
  assert.match(consoleConfigurer, /IDEA 默认使用系统编码解析 Run 控制台/)
  assert.match(consoleConfigurer, /System\.setProperty\(SPRING_PROPERTY, charset\)/)
})
