"""Build a credential-free, implementation-aligned documentation snapshot."""
import copy
import hashlib
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs'
original_bytes = (DOCS / 'backend/backend-v1.openapi.json').read_bytes()
original = json.loads(original_bytes)
spec = copy.deepcopy(original)
spec['paths'] = {
    path: {method: op for method, op in operations.items() if op.get('x-priority') == 'P0'}
    for path, operations in spec['paths'].items()
}
spec['paths'] = {path: ops for path, ops in spec['paths'].items() if ops}
assert sum(map(len, spec['paths'].values())) == 36
spec['info']['title'] = '前端交接用接口快照（患者与医生）'
spec['info']['description'] = '2026-11-05；以当前实现为准。36个业务路由，其中jobs仅404；另附2个图片传输操作。仅SINGLE/JPEG/PNG，医生全库只读，主治关联用于mine筛选；未启用机构成员、短信、审核、云草稿及服务器算法。原规划合同独立保留。本文件不包含账号密码。'
spec['info']['x-contract-sha256'] = hashlib.sha256(original_bytes).hexdigest()
spec['servers'] = [{'url': 'http://127.0.0.1:8080/api/v1', 'description': '仅后端本机；跨电脑联调须替换成双方确认的测试环境地址'}]

paths = spec['paths']
paths['/patients/{patientId}']['delete']['responses'].pop('202', None)
paths['/file-uploads/{uploadId}/complete']['post']['responses'].pop('202', None)
paths['/jobs/{jobId}']['get']['responses'].pop('200', None)
paths['/jobs/{jobId}']['get']['responses']['404'] = {
    'description': '当前P0不生成异步任务，合法未知UUID返回NOT_FOUND',
    'content': {'application/json': {'schema': {'$ref': '#/components/schemas/Error'}}},
}
for path, method, summary in [
    ('/file-uploads', 'post', '初始化JPEG/PNG单文件上传（仅SINGLE）'),
    ('/file-uploads/{uploadId}', 'get', '查询本人上传会话及可用单文件签名'),
    ('/jobs/{jobId}', 'get', '任务查询预留：当前无任务，返回404'),
]:
    paths[path][method]['summary'] = summary

schemas = spec['components']['schemas']
# 收窄指定字段的枚举，使交接快照与当前实现一致。
def enum(name, field, values):
    schemas[name]['properties'][field]['enum'] = values

enum('Profile', 'role', ['PATIENT', 'HOSPITAL_STAFF'])
enum('Capture', 'reviewStatus', ['NOT_SUBMITTED'])
enum('UploadCreate', 'strategy', ['SINGLE'])
enum('UploadCreate', 'mimeType', ['image/jpeg', 'image/png'])
enum('Upload', 'strategy', ['SINGLE'])
enum('Upload', 'state', ['PENDING', 'UPLOADING', 'READY', 'ABORTED'])
enum('File', 'kind', ['IMAGE'])
enum('File', 'state', ['PENDING', 'READY'])
enum('File', 'mimeType', ['image/jpeg', 'image/png'])
enum('Change', 'entityType', ['PATIENT', 'LESION', 'CAPTURE'])
enum('Change', 'operation', ['UPSERT', 'DELETE'])
schemas['UploadComplete']['properties']['parts']['maxItems'] = 0
schemas['ImportCreate']['properties']['targetOrganizationId']['description'] = '当前个人工作区仅省略或null；非空机构ID返回403。'
spec['components']['securitySchemes']['bearerAuth']['bearerFormat'] = 'opaque'

sample = json.loads((DOCS / 'testing/sample-image.json').read_text(encoding='utf-8'))
patient = '11111111-1111-4111-8111-111111111111'
lesion = '22222222-2222-4222-8222-222222222222'
schemas['Login']['example'] = {'account': 'local-owner', 'password': '<由后端负责人提供的API测试密码>'}
schemas['PatientCreate']['example'] = {'id': patient, 'name': '前端联调患者', 'birthDate': None, 'birthYearMonth': '1990-06', 'phoneNumber': '+8613800000000', 'gender': 'UNKNOWN', 'attendingDoctorId': None}
schemas['LesionCreate']['example'] = {'id': lesion, 'patientId': patient, 'bodySite': '左手背', 'note': '虚构数据'}
schemas['UploadCreate']['example'] = {'patientId': patient, **sample, 'strategy': 'SINGLE'}
schemas['UploadComplete']['example'] = {}

# These existing object routes live outside /api/v1 and use URL tickets, not Bearer.
objects = {}
for method, status in [('put', '204'), ('get', '200')]:
    op = {
        'operationId': method + '_signed_image', 'tags': ['图片传输'],
        'summary': '签名图片原始字节' + ('上传' if method == 'put' else '下载'),
        'description': '按上传/下载响应中的完整URL取objectId与ticket。上传objectId为uploadId，下载为fileId。PUT不是form-data；成功后须complete。',
        'security': [], 'x-documentation-only': True,
        'servers': [{'url': 'http://127.0.0.1:8080', 'description': '实际请求使用后端返回的完整签名URL'}],
        'parameters': [
            {'name': 'objectId', 'in': 'path', 'required': True, 'schema': {'type': 'string', 'format': 'uuid'}},
            {'name': 'ticket', 'in': 'query', 'required': True, 'schema': {'type': 'string'}},
        ],
        'responses': {status: {'description': '成功'}, 'default': {'description': '标准错误', 'content': {'application/json': {'schema': {'$ref': '#/components/schemas/Error'}}}}},
    }
    binary = {mime: {'schema': {'type': 'string', 'format': 'binary'}} for mime in ('image/png', 'image/jpeg')}
    if method == 'put':
        op['requestBody'] = {'required': True, 'content': binary}
    else:
        op['responses'][status]['content'] = binary
    objects[method] = op
paths['/objects/{objectId}'] = objects

used_tags = {tag for ops in paths.values() for op in ops.values() for tag in op.get('tags', [])}
spec['tags'] = [tag for tag in spec['tags'] if tag['name'] in used_tags]
spec['tags'].append({'name': '图片传输'})

# 递归收集Schema引用，供裁剪可达模型使用。
def refs(value):
    if isinstance(value, dict):
        if '$ref' in value:
            yield value['$ref']
        for child in value.values():
            yield from refs(child)
    elif isinstance(value, list):
        for child in value:
            yield from refs(child)

needed = set()
todo = list(refs(paths))
while todo:
    ref = todo.pop()
    assert ref.startswith('#/components/schemas/'), ref
    name = ref.rsplit('/', 1)[1]
    if name in needed:
        continue
    needed.add(name)
    todo.extend(refs(schemas[name]))
spec['components']['schemas'] = {name: schema for name, schema in schemas.items() if name in needed}
(DOCS / 'frontend-p0.openapi.json').write_text(json.dumps(spec, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

# Flatten inline object fields as well as documenting all reachable named DTOs.
# 转义Markdown表格单元格中的特殊字符。
def cell(value):
    return str(value).replace('|', '&#124;').replace('\n', ' ')

# 将Schema转换成便于阅读的字段类型。
def typename(schema):
    if '$ref' in schema:
        return schema['$ref'].rsplit('/', 1)[1]
    if schema.get('type') == 'array':
        return 'array<' + typename(schema.get('items', {})) + '>'
    return schema.get('type', 'any') + (f" ({schema['format']})" if 'format' in schema else '')

# 汇总字段的可读限制、枚举和说明。
def constraints(schema):
    out = ['只读，不可提交'] if schema.get('readOnly') else []
    for key, label in [('minLength', '最短字符'), ('maxLength', '最长字符'), ('minItems', '最少项'), ('maxItems', '最多项'), ('pattern', '格式'), ('default', '默认')]:
        if key in schema:
            out.append(f'{label}={schema[key]}')
    if 'minimum' in schema:
        out.append(('>' if schema.get('exclusiveMinimum') else '>=') + str(schema['minimum']))
    if 'maximum' in schema:
        out.append(('<' if schema.get('exclusiveMaximum') else '<=') + str(schema['maximum']))
    if 'enum' in schema:
        out.append('枚举=' + ', '.join(map(str, schema['enum'])))
    if 'description' in schema:
        out.append(schema['description'])
    if schema.get('additionalProperties') is False:
        out.append('不接收未定义字段')
    elif isinstance(schema.get('additionalProperties'), dict):
        out.append('额外键的值类型=' + typename(schema['additionalProperties']))
    return cell('；'.join(out) or '—')

# 递归生成对象及内嵌数组字段的字典表格行。
def rows(schema, prefix=''):
    required = set(schema.get('required', []))
    result = []
    for name, field in schema.get('properties', {}).items():
        key = prefix + name
        result.append(f"| `{key}` | {cell(typename(field))} | {'是' if name in required else '否'} | {'是' if field.get('nullable') else '否'} | {constraints(field)} |")
        if field.get('type') == 'object' and field.get('properties'):
            result.extend(rows(field, key + '.'))
        if field.get('type') == 'array' and field.get('items', {}).get('properties'):
            result.extend(rows(field['items'], key + '[].'))
    return result

lines = [
    '# 前端联调字段字典（当前P0快照）', '',
    '版本：2026-11-05 / API v1.1.0。与 [接口清单](frontend-api-alignment.md) 及 [OpenAPI快照](frontend-p0.openapi.json) 配套使用。', '',
    '本表从当前P0导出Schema提取，必填/可空分开列出。“必填”指该对象存在时的合同要求；可选对象省略时，不要求其子字段。Patch嵌套字段按补丁DTO递归可选，但合并后的完整资源仍须通过验证。', '',
    '下表包含请求与响应模型，不能把响应模型的所有字段回传给创建/补丁接口。UUID为字符串，int64时间为Unix毫秒；数组整体替换，未知字段禁止或依具体additionalProperties规则处理。', '',
    '创建患者/病灶/采集：客户端生成新UUID。File/Upload/Snapshot/ImportPlan：服务器生成ID。Profile没有revision字段，版本从响应头ETag获取。', '',
    '普通列表Page<T>＝`{"items":[T],"nextPageToken":string|null}`。一般limit默认50、最大100；增量默认100。DTO字段的空值不表示0或false。', '',
    '实现约束补充：新密码至少12字符且UTF-8最多72字节；图像总像素<=40000000、宽高各<=20000。valid analysis还需尺度、几何和面积一致性校验。targetOrganizationId当前仅省略/null。更完整业务约束见主清单，Schema不能单独表达这些关系。', '',
]
for name, schema in spec['components']['schemas'].items():
    lines += [f'## {name}', '', '| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |', '|---|---|---|---|---|']
    lines += rows(schema)
    lines.append('')
(DOCS / 'frontend-field-reference.md').write_text('\n'.join(lines), encoding='utf-8')

disabled = []
for path, ops in original['paths'].items():
    for method, op in ops.items():
        if op.get('x-priority') in ('P1', 'P2'):
            disabled.append((method.upper(), path, op['x-priority'], op['summary']))
assert len(disabled) == 40
appendix = ['| 优先级 | 方法与路径 | 规划用途 | 当前状态 |', '|---|---|---|---|']
appendix += [f'| {priority} | `{method} {path}` | {cell(summary)} | 未启用，不能按成功流程验收 |' for method, path, priority, summary in disabled]
main = DOCS / 'frontend-api-alignment.md'
main.write_text(main.read_text(encoding='utf-8').replace('<!-- DISABLED_OPERATIONS -->', '\n'.join(appendix)), encoding='utf-8')

# Include handoff files and existing tutorials; never include local.ps1 or secrets.
entries = [
    'frontend-api-alignment.md', 'frontend-field-reference.md', 'frontend-p0.openapi.json',
    'patient-doctor-guide.md', 'swagger-ui.md', 'api-testing.md', 'gui-running.md', 'implementation-status.md',
    'testing/sample-20x20.png', 'testing/sample-image.json', 'testing/followup-p0.postman_collection.json',
    'backend/backend-v1.openapi.json', 'backend/README.md', 'backend/api-catalog.md', 'backend/validation.md',
]
with zipfile.ZipFile(DOCS / 'frontend-api-handoff.zip', 'w', zipfile.ZIP_DEFLATED) as bundle:
    bundle.writestr('README.md', '# 前端接口联调交接包\n\n先阅读 [后端接口清单](frontend-api-alignment.md)，字段见 frontend-field-reference.md。\n\n导入接口工具请使用 frontend-p0.openapi.json（36个P0业务路由+2个图片操作）；backend目录是76项合同（含新增医生目录），仅供背景参考，不能视作已实现。\n\nSwagger地址仅后端本机有效；跨机地址、账号密码和访问方式由双方另行确认。包内不含真实密码、令牌或数据库配置。\n')
    for entry in entries:
        bundle.write(DOCS / entry, entry)
assert (DOCS / 'backend/backend-v1.openapi.json').read_bytes() == original_bytes
print(f'Built handoff: 36 P0 routes + 2 object operations, {len(needed)} DTOs, {len(disabled)} disabled operations; original contract unchanged.')
