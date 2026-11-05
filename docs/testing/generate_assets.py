"""Generate local debugging assets without credentials or third-party packages."""
import hashlib
import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent


def png_chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)


image = (b'\x89PNG\r\n\x1a\n'
         + png_chunk(b'IHDR', struct.pack('>IIBBBBB', 20, 20, 8, 2, 0, 0, 0))
         + png_chunk(b'IDAT', zlib.compress((b'\x00' + b'\x80\xa0\xc0' * 20) * 20))
         + png_chunk(b'IEND', b''))
(HERE / 'sample-20x20.png').write_bytes(image)
sample = dict(sizeBytes=len(image), sha256=hashlib.sha256(image).hexdigest(), mimeType='image/png', width=20, height=20)
(HERE / 'sample-image.json').write_text(json.dumps(sample, indent=2) + '\n', encoding='utf-8')

NO_AUTH = {'type': 'noauth'}
covered = set()


def request(name, method, path, status=200, body=None, *, idem=None, revision=None,
            save=None, resource=None, init=(), auth=True, binary=False, description=''):
    headers = []
    before = []
    if idem:
        before.append(f"if (!pm.collectionVariables.get('idem_{idem}')) pm.collectionVariables.set('idem_{idem}', pm.variables.replaceIn('{{$guid}}'));".replace('{$guid}', '{{$guid}}'))
        headers.append({'key': 'Idempotency-Key', 'value': '{{idem_' + idem + '}}'})
    for variable in init:
        before.append(f"if (!pm.collectionVariables.get('{variable}')) pm.collectionVariables.set('{variable}', pm.variables.replaceIn('{{$guid}}'));".replace('{$guid}', '{{$guid}}'))
    if revision:
        headers.append({'key': 'If-Match', 'value': '{{' + revision + '}}'})
    url = path if path.startswith('{{') else '{{baseUrl}}' + path
    req = {'method': method, 'header': headers, 'url': url, 'description': description}
    if not auth:
        req['auth'] = NO_AUTH
    if body is not None:
        headers.append({'key': 'Content-Type', 'value': 'application/merge-patch+json' if method == 'PATCH' else 'application/json'})
        req['body'] = {'mode': 'raw', 'raw': json.dumps(body, ensure_ascii=False, indent=2), 'options': {'raw': {'language': 'json'}}}
    if binary:
        headers.append({'key': 'Content-Type', 'value': '{{uploadContentType}}'})
        req['body'] = {'mode': 'file', 'file': {'src': ''}}
    after = [f"pm.test('HTTP {status}', function () {{ pm.response.to.have.status({status}); }});"]
    if save or resource:
        after.append(f'if (pm.response.code === {status}) {{')
        if save:
            after.extend(save.splitlines())
        if resource:
            after.append(f"pm.collectionVariables.set('{resource}Etag', pm.response.headers.get('ETag')); ")
        after.append('}')
    events = [{'listen': 'test', 'script': {'type': 'text/javascript', 'exec': after}}]
    if before:
        events.insert(0, {'listen': 'prerequest', 'script': {'type': 'text/javascript', 'exec': before}})
    # Track canonical contract routes separately from dynamic Postman variables.
    if not path.startswith('{{'):
        import re
        canonical = re.sub(r'\{\{(\w+)\}\}', r'{\1}', path.split('?')[0])
        covered.add((method.lower(), canonical))
    return {'name': name, 'request': req, 'event': events}


def folder(name, items, description=''):
    return {'name': name, 'description': description, 'item': items}


save_tokens = """const data = pm.response.json();
pm.collectionVariables.set('accessToken', data.accessToken);
pm.collectionVariables.set('refreshToken', data.refreshToken);
pm.collectionVariables.set('tokenExpiresAt', String(data.expiresAt));"""
save_upload = """const data = pm.response.json();
pm.collectionVariables.set('uploadId', data.id);
pm.collectionVariables.set('fileId', data.fileId);
pm.collectionVariables.set('uploadUrl', data.uploadUrl);
pm.collectionVariables.set('uploadContentType', data.requiredHeaders['Content-Type']);"""
save_snapshot_page = """const data = pm.response.json();
pm.collectionVariables.set('snapshotPageToken', data.nextPageToken || '');
if (data.nextCursor) pm.collectionVariables.set('syncCursor', data.nextCursor);"""

folders = [
    folder('01 启动检查与登录', [
        request('01 健康检查（无需登录）', 'GET', '/system/health', auth=False),
        request('02 登录并保存双令牌', 'POST', '/auth/login', body={'account': '{{account}}', 'password': '{{password}}'}, auth=False, save=save_tokens),
        request('03 查询当前账号并保存资料 ETag', 'GET', '/auth/me', resource='profile'),
        request('04 查询已启用能力', 'GET', '/system/capabilities'),
    ]),
    folder('02 患者与病灶', [
        request('01 创建测试患者', 'POST', '/patients', 201, {'id': '{{patientId}}', 'name': '接口调试患者', 'birthDate': '1990-01-01'}, idem='patientCreate', init=['patientId'], resource='patient'),
        request('02 查询患者详情', 'GET', '/patients/{{patientId}}', resource='patient'),
        request('03 搜索患者列表', 'GET', '/patients?q=接口调试&limit=10'),
        request('04 修改患者', 'PATCH', '/patients/{{patientId}}', body={'name': '接口调试患者（已修改）', 'birthDate': None}, revision='patientEtag', resource='patient'),
        request('05 创建病灶', 'POST', '/lesions', 201, {'id': '{{lesionId}}', 'patientId': '{{patientId}}', 'bodySite': '左手背', 'note': '调试用虚构数据'}, idem='lesionCreate', init=['lesionId'], resource='lesion'),
        request('06 查询病灶详情', 'GET', '/lesions/{{lesionId}}', resource='lesion'),
        request('07 查询患者的病灶列表', 'GET', '/patients/{{patientId}}/lesions?limit=10'),
        request('08 修改病灶', 'PATCH', '/lesions/{{lesionId}}', body={'note': '调试备注已修改'}, revision='lesionEtag', resource='lesion'),
    ]),
    folder('03 图片上传与采集', [
        request('01 创建单文件上传会话', 'POST', '/file-uploads', 201, {'patientId': '{{patientId}}', **sample, 'strategy': 'SINGLE'}, idem='uploadCreate', save=save_upload),
        request('02 查询上传会话并更新签名', 'GET', '/file-uploads/{{uploadId}}', save=save_upload),
        request('03 PUT 测试图片（Body 选择 binary 文件）', 'PUT', '{{uploadUrl}}', 204, auth=False, binary=True, description='Body → binary → 选择 docs/testing/sample-20x20.png。必须上传原始文件，保持 Content-Type=image/png。'),
        request('04 确认上传并校验文件', 'POST', '/file-uploads/{{uploadId}}/complete', body={}, idem='uploadComplete'),
        request('05 查询文件元数据', 'GET', '/files/{{fileId}}'),
        request('06 申请下载地址', 'POST', '/files/{{fileId}}/download-url', save="pm.collectionVariables.set('downloadUrl', pm.response.json().url);"),
        request('07 下载图片（无需 Bearer）', 'GET', '{{downloadUrl}}', auth=False),
        request('08 创建正式采集', 'POST', '/captures', 201, {'id': '{{captureId}}', 'lesionId': '{{lesionId}}', 'imageFileId': '{{fileId}}', 'capturedAt': 1700000000000, 'deviceModel': 'Postman-Debug', 'source': 'GALLERY', 'assetKind': 'CAPTURE_IMAGE', 'referenceOnly': False, 'qualityNote': '合成图片，仅用于接口联调', 'calibration': None, 'analysis': None, 'annotation': None, 'metadata': {}}, idem='captureCreate', init=['captureId'], resource='capture'),
        request('09 查询采集详情', 'GET', '/captures/{{captureId}}', resource='capture'),
        request('10 查询病灶采集列表', 'GET', '/lesions/{{lesionId}}/captures?limit=10&fromAt=0&toAt=2000000000000'),
        request('11 修改采集备注', 'PATCH', '/captures/{{captureId}}', body={'qualityNote': '已完成接口联调'}, revision='captureEtag', resource='capture'),
    ], '按顺序逐条发送。第03条需要手动选取附件图片；请在上传签名到期前完成 PUT。'),
    folder('04 快照与增量同步', [
        request('01 创建一致快照', 'POST', '/sync/snapshots', 201, idem='snapshotCreate', save="pm.collectionVariables.set('snapshotId', pm.response.json().id);\npm.collectionVariables.unset('snapshotPageToken');\npm.collectionVariables.unset('syncCursor');"),
        request('02 读取快照首页', 'GET', '/sync/snapshots/{{snapshotId}}?limit=100', save=save_snapshot_page),
        request('03 读取快照下一页（有 token 才发送）', 'GET', '/sync/snapshots/{{snapshotId}}?limit=100&pageToken={{snapshotPageToken}}', save=save_snapshot_page, description='仅当上一页 nextPageToken 非 null 时发送，重复直到末页拿到 syncCursor。'),
        request('04 拉取增量并更新游标', 'GET', '/sync/changes?limit=100&cursor={{syncCursor}}', save="pm.collectionVariables.set('syncCursor', pm.response.json().nextCursor);"),
    ]),
    folder('05 按需测试（不要整组运行）', [
        request('01 刷新并替换双令牌', 'POST', '/auth/refresh', body={'refreshToken': '{{refreshToken}}'}, auth=False, save=save_tokens, description='每次都使用最新 refreshToken；重复旧令牌会撤销整个令牌族。'),
        request('02 修改资料', 'PATCH', '/users/me', body={'displayName': '接口调试用户'}, revision='profileEtag', resource='profile'),
        request('03 修改登录密码（会更改真实账号）', 'POST', '/users/me/password', 204, {'currentPassword': '{{password}}', 'newPassword': '{{newPassword}}'}, idem='passwordChange', description='先设置本地 newPassword。成功后手动更新 password；其他会话会失效。'),
        request('04 创建旧数据导入计划', 'POST', '/offline-imports', 201, {'clientDatasetId': '{{clientDatasetId}}', 'confirmedPatientIds': ['{{importPatientId}}']}, idem='importCreate', init=['clientDatasetId', 'importPatientId'], save="pm.collectionVariables.set('importId', pm.response.json().id);", description='将返回 importId 与这里的 importPatientId 用于后续 POST /patients；本请求仅创建计划。'),
        request('05 上传续签（仅完成前）', 'POST', '/file-uploads/{{uploadId}}/sign-single', 201, idem='uploadSign', save="const data = pm.response.json();\npm.collectionVariables.set('uploadUrl', data.url);\npm.collectionVariables.set('uploadContentType', data.requiredHeaders['Content-Type']);", description='每次需要全新的签名时清空 idem_uploadSign。会话 READY 后返回409。'),
        request('06 中止上传（仅未确认会话）', 'DELETE', '/file-uploads/{{uploadId}}', 204, description='会中止当前 uploadId，不要在正式上传流程中误发；READY 会返回409。'),
        request('07 查询未知任务（预期404）', 'GET', '/jobs/{{jobId}}', 404, init=['jobId']),
    ]),
    folder('06 清理测试记录与退出', [
        request('01 删除测试采集', 'DELETE', '/captures/{{captureId}}', 204, revision='captureEtag'),
        request('02 删除测试病灶', 'DELETE', '/lesions/{{lesionId}}', 204, revision='lesionEtag'),
        request('03 删除测试患者', 'DELETE', '/patients/{{patientId}}', 204, revision='patientEtag', description='会级联删除仍存在的子病灶和采集。删除后仍可在增量同步中查看 DELETE 墓碑。'),
        request('04 退出当前登录', 'POST', '/auth/logout', 204, {'refreshToken': '{{refreshToken}}'}, save="pm.collectionVariables.unset('accessToken');\npm.collectionVariables.unset('refreshToken');"),
    ], '最后执行。只操作本次创建的虚构数据；如选择直接删除患者，前两条不再需要执行。'),
]

folders.insert(1, folder('医生选择与权限范围', [
    request('01 查询可选主治医师', 'GET', '/doctors?limit=50', description='仅返回有效医生ID和显示名；创建患者时可选填attendingDoctorId。'),
    request('02 医生查看全部患者', 'GET', '/patients?scope=all&limit=50', description='医生返回全库；普通患者仍仅返回本账号数据。'),
    request('03 医生查看主治名下患者', 'GET', '/patients?scope=mine&limit=50', description='医生按主治关联筛选；普通患者始终限定本账号。'),
]))
contract = json.loads((ROOT / 'docs/backend/backend-v1.openapi.json').read_text(encoding='utf-8'))
expected = {(method, path) for path, ops in contract['paths'].items() for method, op in ops.items() if isinstance(op, dict) and op.get('x-priority') == 'P0'}
assert covered == expected, f'Coverage mismatch: missing={expected - covered}, extra={covered - expected}'
variables = {'baseUrl': 'http://127.0.0.1:8080/api/v1', 'account': 'local-owner', 'password': '', 'newPassword': ''}
for name in ['accessToken', 'refreshToken', 'tokenExpiresAt', 'patientId', 'lesionId', 'captureId', 'patientEtag', 'lesionEtag', 'captureEtag', 'profileEtag', 'uploadId', 'fileId', 'uploadUrl', 'uploadContentType', 'downloadUrl', 'snapshotId', 'snapshotPageToken', 'syncCursor', 'clientDatasetId', 'importPatientId', 'importId', 'jobId']:
    variables[name] = ''
collection = {
    'info': {'name': '皮肤病随访后端 P0 调试', 'schema': 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json', 'description': '对应 docs/api-testing.md，覆盖36个P0合同操作及签名对象读写，包含医生选择和all/mine筛选。手动按文件夹顺序发送，图片上传需选择本地文件。按需测试和清理文件夹不要直接整组运行。集合不含真实密码。'},
    'auth': {'type': 'bearer', 'bearer': [{'key': 'token', 'value': '{{accessToken}}', 'type': 'string'}]},
    'variable': [{'key': key, 'value': value, 'type': 'string'} for key, value in variables.items()],
    'item': folders,
}
(HERE / 'followup-p0.postman_collection.json').write_text(json.dumps(collection, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'Generated {len(expected)} P0 operations, {sum(len(f["item"]) for f in folders)} requests; fixture: {sample}')
