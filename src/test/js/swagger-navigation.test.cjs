const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const navigation = require('../../main/resources/static/swagger-ui/swagger-navigation.js');
const spec = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../../../docs/frontend-p0.openapi.json'), 'utf8'));

// 验证导航完整列出当前接口且不显示空的规划模块。
test('navigation lists every available operation once and omits empty planning groups', () => {
  const operations = navigation.buildOperations(spec);
  assert.equal(operations.length, 38);
  assert.equal(new Set(operations.map(operation => operation.key)).size, 38);
  assert(!operations.some(operation => ['机构', '草稿', '审核'].includes(operation.tag)));
  assert.equal(operations.find(operation => operation.path === '/auth/login').requiresAuth, false);
  assert.equal(operations.find(operation => operation.method === 'GET' && operation.path === '/patients').requiresAuth, true);
  assert.equal(operations.find(operation => operation.method === 'PUT' && operation.path.startsWith('/objects/')).requiresAuth, false);
});

// 验证中文、路径、方法组合搜索及空结果。
test('search matches Chinese names, paths and methods together without case sensitivity', () => {
  const operations = navigation.buildOperations(spec);
  const patientPost = navigation.filterOperations(operations, 'post /patients');
  assert.equal(patientPost.length, 1);
  assert.equal(patientPost[0].path, '/patients');
  assert(navigation.filterOperations(operations, '患者').length > 0);
  assert(navigation.filterOperations(operations, 'GET /captures').length > 0);
  assert.equal(navigation.filterOperations(operations, 'no-such-route').length, 0);
  assert.equal(navigation.filterOperations(operations, '   ').length, 38);
});

// 验证接口链接恢复及异常编码容错。
test('deep links restore the exact operation and malformed or unknown links are harmless', () => {
  const operations = navigation.buildOperations(spec);
  for (const operation of operations) {
    assert.equal(navigation.operationFromHash(operations, navigation.operationHash(operation)).key, operation.key);
  }
  assert.equal(navigation.operationFromHash(operations, '#/%E0%A4%A/bad'), null);
  assert.equal(navigation.operationFromHash(operations, '#/missing/no-operation'), null);
  assert.equal(navigation.operationFromHash(operations, ''), null);
});
