"""接口文档生成器：从本文件目录生成清单和 OpenAPI；不是后端服务。修改合同后重新运行，并进行标准校验。"""
import json, re
from pathlib import Path

root = Path(__file__).resolve().parent
S = {}
def ref(n): return {'$ref': '#/components/schemas/'+n}
def string(**kw): return dict(type='string', **kw)
def integer(**kw): return dict(type='integer', format='int64', **kw)
def enum(*v): return string(enum=list(v))
def array(t): return {'type':'array','items':t}
def obj(props, required=()):
    result={'type':'object','properties':props,'additionalProperties':False}
    if required: result['required']=list(required)
    return result
def add(n,p,r=()): S[n]=obj(p,r)
def nullable(n): return dict(S[n], nullable=True)
uid=string(format='uuid'); text=string(maxLength=2000); timestamp=integer(minimum=0)
revision=integer(minimum=1); boolean={'type':'boolean'}; number={'type':'number','format':'double'}
add('Error',dict(code=string(),message=string(),requestId=string(),details={'type':'object','additionalProperties':True}),['code','message','requestId'])
add('Profile',dict(id=uid,account=string(),displayName=string(),role=enum('PATIENT','HOSPITAL_STAFF'),organizationId=dict(uid,nullable=True),permissions=array(string())),['id','account','displayName','role','organizationId','permissions'])
add('Login',dict(account=string(minLength=1,maxLength=128),password=string(format='password',minLength=1),requestedRole=enum('PATIENT','HOSPITAL_STAFF')),['account','password'])
add('Tokens',dict(profile=ref('Profile'),accessToken=string(),refreshToken=string(),expiresAt=timestamp),['profile','accessToken','refreshToken','expiresAt'])
add('Refresh',dict(refreshToken=string(minLength=1)),['refreshToken'])
add('ChallengeRequest',dict(destination=string(),purpose=enum('REGISTER','RESET_PASSWORD')),['destination','purpose'])
add('Challenge',dict(challengeId=uid,expiresAt=timestamp,retryAfterSeconds=integer()),['challengeId','expiresAt','retryAfterSeconds'])
add('Register',dict(account=string(),password=string(format='password'),displayName=string(),challengeId=uid,code=string()),['account','password','displayName','challengeId','code'])
add('ResetPassword',dict(challengeId=uid,code=string(),newPassword=string(format='password')),['challengeId','code','newPassword'])
add('ProfilePatch',dict(displayName=string(minLength=1,maxLength=128)))
add('PasswordChange',dict(currentPassword=string(format='password'),newPassword=string(format='password')),['currentPassword','newPassword'])
add('SessionInfo',dict(id=uid,deviceId=string(),createdAt=timestamp,expiresAt=timestamp,current=boolean),['id','createdAt','expiresAt','current'])
add('OrganizationSwitch',dict(organizationId=uid),['organizationId'])
base=dict(id=uid,revision=revision,createdAt=timestamp,updatedAt=timestamp,deletedAt=dict(timestamp,nullable=True))
add('Organization',dict(base,name=string(),status=enum('ACTIVE','DISABLED')),['id','revision','name','status'])
add('OrganizationCreate',dict(name=string(minLength=1,maxLength=200)),['name'])
add('OrganizationPatch',dict(name=string(minLength=1,maxLength=200),status=enum('ACTIVE','DISABLED')))
add('Membership',dict(base,organizationId=uid,userId=uid,membershipRole=enum('CLINICIAN','REVIEWER','ORG_ADMIN'),status=enum('ACTIVE','DISABLED','INVITED')),['id','revision','organizationId','userId','membershipRole','status'])
add('MembershipCreate',dict(userId=uid,membershipRole=enum('CLINICIAN','REVIEWER','ORG_ADMIN')),['userId','membershipRole'])
add('MembershipPatch',dict(membershipRole=enum('CLINICIAN','REVIEWER','ORG_ADMIN'),status=enum('ACTIVE','DISABLED')))
add('Patient',dict(base,name=string(),birthDate=string(format='date',nullable=True)),['id','name','birthDate','revision','createdAt','updatedAt'])
add('PatientCreate',dict(id=uid,name=string(minLength=1,maxLength=128),birthDate=string(format='date',nullable=True),clientCreatedAt=timestamp,importId=uid),['id','name'])
add('PatientPatch',dict(name=string(minLength=1,maxLength=128),birthDate=string(format='date',nullable=True)))
add('PatientAccount',dict(base,patientId=uid,userId=uid,relation=enum('SELF','GUARDIAN','DELEGATE'),verifiedAt=timestamp),['id','revision','patientId','userId','relation'])
add('PatientAccountCreate',dict(userId=uid,relation=enum('SELF','GUARDIAN','DELEGATE'),verificationId=uid),['userId','relation','verificationId'])
add('LinkVerificationCreate',dict(userId=uid,relation=enum('SELF','GUARDIAN','DELEGATE'),evidenceReference=string(maxLength=256)),['userId','relation','evidenceReference'])
add('LinkVerification',dict(base,patientId=uid,userId=uid,relation=enum('SELF','GUARDIAN','DELEGATE'),status=enum('PENDING','VERIFIED','REJECTED','CONSUMED'),expiresAt=timestamp),['id','revision','patientId','userId','relation','status','expiresAt'])
add('LinkVerificationDecision',dict(decision=enum('VERIFIED','REJECTED'),note=text),['decision','note'])
add('Grant',dict(base,patientId=uid,organizationId=uid,granteeUserId=dict(uid,nullable=True),permissions=array(string()),status=enum('ACTIVE','REVOKED')),['id','revision','patientId','organizationId','permissions','status'])
add('GrantCreate',dict(organizationId=uid,granteeUserId=dict(uid,nullable=True),permissions=array(string())),['organizationId','permissions'])
add('Lesion',dict(base,patientId=uid,bodySite=string(),note=text),['id','revision','patientId','bodySite','note','createdAt','updatedAt'])
add('LesionCreate',dict(id=uid,patientId=uid,bodySite=string(minLength=1,maxLength=128),note=text,clientCreatedAt=timestamp,importId=uid),['id','patientId','bodySite'])
add('LesionPatch',dict(bodySite=string(minLength=1,maxLength=128),note=text))
add('Point',dict(x=dict(number,minimum=0),y=dict(number,minimum=0)),['x','y'])
add('Lab',dict(l=number,a=number,b=number),['l','a','b'])
add('Rgb',{c:{'type':'integer','minimum':0,'maximum':255} for c in ('r','g','b')},['r','g','b'])
add('ColorCorrection',{c:number for c in ('redScale','redOffset','greenScale','greenOffset','blueScale','blueOffset')},['redScale','redOffset','greenScale','greenOffset','blueScale','blueOffset'])
add('Calibration',dict(markerId=string(),markerWidthMm=dict(number,exclusiveMinimum=True,minimum=0),pixelsPerMm=dict(number,exclusiveMinimum=True,minimum=0),qualityScore=dict(number,minimum=0,maximum=1),colorReference=array(ref('Lab')),swatchRgb=array(ref('Rgb')),colorCorrection=nullable('ColorCorrection'),method=enum('AUTO_MARKER','MANUAL','UNKNOWN'),markerCorners=array(ref('Point')),scalePoints=array(ref('Point')),calibrationVersion=string()),['markerId','markerWidthMm','pixelsPerMm','qualityScore'])
add('Analysis',dict(areaMm2=dict(number,nullable=True,minimum=0),lesionColor=nullable('Lab'),colorDifference=dict(number,nullable=True,minimum=0),algorithmVersion=string(),isValid=boolean,failureReason=string(nullable=True)),['isValid','algorithmVersion'])
add('Annotation',dict(points=dict(array(ref('Point')),maxItems=2000),imageWidth=integer(minimum=1),imageHeight=integer(minimum=1),segmentationVersion=string(nullable=True),edited=boolean),['points','imageWidth','imageHeight','edited'])
add('Qc',dict(liveGateExecuted=dict(boolean,nullable=True),passed=dict(boolean,nullable=True),issues=array(string()),sharpness=dict(number,nullable=True),meanExposure=dict(number,nullable=True,minimum=0,maximum=1),clippedPixelRatio=dict(number,nullable=True,minimum=0,maximum=1),markerDetected=dict(boolean,nullable=True),targetSizeRatio=dict(number,nullable=True),perspectiveRatio=dict(number,nullable=True)))
add('CaptureMetadata',dict(appVersion=string(),algorithmVersions={'type':'object','additionalProperties':{'type':'string'}},collectedAt=dict(timestamp,nullable=True),importedAt=dict(timestamp,nullable=True),originalCapturedAt=dict(timestamp,nullable=True),liveQc=nullable('Qc')))
capture=dict(id=uid,lesionId=uid,imageFileId=uid,capturedAt=timestamp,deviceModel=string(maxLength=256),source=enum('CAMERA','GALLERY','UNKNOWN'),assetKind=enum('CAPTURE_IMAGE','ANALYSIS_COPY','UNKNOWN'),referenceOnly=dict(boolean,nullable=True),qualityNote=string(nullable=True,maxLength=2000),calibration=nullable('Calibration'),analysis=nullable('Analysis'),annotation=nullable('Annotation'),metadata=ref('CaptureMetadata'))
add('CaptureCreate',dict(capture,importId=uid),['id','lesionId','imageFileId','capturedAt','deviceModel','source','assetKind','referenceOnly'])
add('Capture',dict(base,**{k:v for k,v in capture.items() if k!='id'},reviewStatus=enum('NOT_SUBMITTED','PENDING','APPROVED','REJECTED')),['id','revision','lesionId','imageFileId','capturedAt','source','assetKind','referenceOnly','reviewStatus','createdAt','updatedAt'])
add('CapturePatch',{k:capture[k] for k in ('qualityNote','calibration','analysis','annotation','metadata')})
add('File',dict(id=uid,kind=enum('IMAGE','EXPORT_ARCHIVE','ALGORITHM_ARTIFACT'),sizeBytes=integer(minimum=1),sha256=string(pattern='^[a-f0-9]{64}$'),mimeType=enum('image/jpeg','image/png','image/webp','application/zip','application/json'),width=integer(minimum=1,nullable=True),height=integer(minimum=1,nullable=True),state=enum('PENDING','VERIFYING','READY','FAILED','DELETED'),createdAt=timestamp),['id','kind','sizeBytes','sha256','mimeType','width','height','state'])
add('UploadCreate',dict(patientId=uid,sizeBytes=integer(minimum=1,maximum=104857600),sha256=string(pattern='^[a-f0-9]{64}$'),mimeType=enum('image/jpeg','image/png','image/webp'),width=integer(minimum=1),height=integer(minimum=1),strategy=enum('SINGLE','MULTIPART')),['patientId','sizeBytes','sha256','mimeType','width','height','strategy'])
add('Part',dict(partNumber=integer(minimum=1),etag=string(),sha256=string(pattern='^[a-f0-9]{64}$')),['partNumber','etag','sha256'])
add('Upload',dict(id=uid,fileId=uid,strategy=enum('SINGLE','MULTIPART'),state=enum('PENDING','UPLOADING','VERIFYING','READY','FAILED','ABORTED'),uploadUrl=string(format='uri',nullable=True),requiredHeaders={'type':'object','additionalProperties':{'type':'string'}},expiresAt=timestamp,partSizeBytes=integer(minimum=1),maxParts=integer(minimum=1),acceptedParts=array(ref('Part')),error=nullable('Error')),['id','fileId','strategy','state','expiresAt','requiredHeaders'])
add('SignParts',dict(partNumbers=dict(array(integer(minimum=1)),maxItems=100,minItems=1)),['partNumbers'])
add('PartUrl',dict(partNumber=integer(minimum=1),url=string(format='uri'),expiresAt=timestamp,requiredHeaders={'type':'object','additionalProperties':{'type':'string'}}),['partNumber','url','expiresAt','requiredHeaders'])
add('PartUrls',dict(items=array(ref('PartUrl'))),['items'])
add('UploadComplete',dict(parts=array(ref('Part'))))
add('UploadUrl',dict(url=string(format='uri'),urlExpiresAt=timestamp,requiredHeaders={'type':'object','additionalProperties':{'type':'string'}}),['url','urlExpiresAt','requiredHeaders'])
S['Upload']['properties']['urlExpiresAt']=dict(timestamp,nullable=True)
S['Capture']['properties']['contentRevision']=revision
S['Capture']['required'].append('contentRevision')
add('DownloadUrl',dict(url=string(format='uri'),expiresAt=timestamp,sha256=string(),sizeBytes=integer(),mimeType=string()),['url','expiresAt','sha256','sizeBytes','mimeType'])
add('Editor',dict(schemaVersion={'type':'integer','enum':[1]},points=dict(array(ref('Point')),maxItems=2000),scalePoints=dict(array(ref('Point')),maxItems=2),referencePoint=nullable('Point'),markerMm=string(),segmentationVersion=string(nullable=True),edited=boolean,confirmed=boolean,step={'type':'integer','minimum':0,'maximum':2},mode=string()),['schemaVersion','points','scalePoints','edited','confirmed','step','mode'])
add('DraftCreate',dict(id=uid,lesionId=uid,imageFileId=uid,editorState=ref('Editor'),referenceOnly=boolean,qualityNote=string(nullable=True)),['id','lesionId','imageFileId','editorState','referenceOnly'])
add('Draft',dict(base,lesionId=uid,imageFileId=uid,editorState=ref('Editor'),referenceOnly=boolean,qualityNote=string(nullable=True)),['id','revision','lesionId','imageFileId','editorState','referenceOnly'])
add('DraftPatch',dict(editorState=ref('Editor'),qualityNote=string(nullable=True)))
add('ReviewSubmit',dict(captureContentRevision=revision),['captureContentRevision'])
add('ReviewDecision',dict(captureContentRevision=revision,decision=enum('APPROVED','REJECTED'),note=string(maxLength=2000)),['captureContentRevision','decision','note'])
add('Review',dict(base,captureId=uid,captureContentRevision=revision,reviewerId=dict(uid,nullable=True),status=enum('PENDING','APPROVED','REJECTED','VOID'),note=text),['id','revision','captureId','captureContentRevision','status'])
add('TrendPoint',dict(captureId=uid,capturedAt=timestamp,areaMm2=dict(number,nullable=True),colorDifference=dict(number,nullable=True),revision=revision),['captureId','capturedAt','revision'])
add('ImportCreate',dict(clientDatasetId=uid,confirmedPatientIds=dict(array(uid),minItems=1,maxItems=100),targetOrganizationId=dict(uid,nullable=True)),['clientDatasetId','confirmedPatientIds'])
add('ImportPlan',dict(id=uid,allowedPatientIds=array(uid),expiresAt=timestamp),['id','allowedPatientIds','expiresAt'])
add('Snapshot',dict(id=uid,expiresAt=timestamp),['id','expiresAt'])
entities=enum('PATIENT','LESION','CAPTURE','DRAFT','PATIENT_ACCOUNT','PATIENT_GRANT','REVIEW')
payload={'oneOf':[ref(n) for n in ['Patient','Lesion','Capture','Draft','PatientAccount','Grant','Review']]}
# oneOf 的具体资源由 entityType 决定，服务端还需交叉验证。
add('Change',dict(entityType=entities,entityId=uid,operation=enum('UPSERT','DELETE','ACCESS_REVOKED'),revision=revision,payload={'type':'object','nullable':True,'description':'UPSERT 为对应 entityType 的完整资源；DELETE/ACCESS_REVOKED 为 null。','additionalProperties':True}),['entityType','entityId','operation','revision','payload'])
add('SnapshotPage',dict(snapshotId=uid,items=array(ref('Change')),nextPageToken=string(nullable=True),nextCursor=string(nullable=True)),['snapshotId','items','nextPageToken','nextCursor'])
add('Changes',dict(items=array(ref('Change')),nextCursor=string(),hasMore=boolean),['items','nextCursor','hasMore'])
add('Mutation',dict(mutationId=uid,entityType=enum('PATIENT','LESION','CAPTURE','DRAFT'),entityId=uid,operation=enum('CREATE','PATCH','DELETE'),baseRevision=dict(revision,nullable=True),dependsOn=array(uid),payload={'type':'object','nullable':True,'additionalProperties':True,'description':'匹配资源 Create/Patch DTO；DELETE 为 null；必须执行同 REST 接口字段白名单。'}),['mutationId','entityType','entityId','operation','baseRevision','dependsOn','payload'])
add('Push',dict(mutations=dict(array(ref('Mutation')),minItems=1,maxItems=100)),['mutations'])
add('MutationResult',dict(mutationId=uid,status=enum('APPLIED','CONFLICT','REJECTED','DEPENDENCY_FAILED'),entity=nullable('Change'),error=nullable('Error')),['mutationId','status'])
add('PushResult',dict(results=array(ref('MutationResult'))),['results'])
add('Reanalysis',dict(captureContentRevision=revision,algorithmId=string(),algorithmVersion=string()),['captureContentRevision','algorithmId','algorithmVersion'])
add('Alignment',dict(referenceCaptureId=uid,referenceContentRevision=revision,movingCaptureId=uid,movingContentRevision=revision,algorithmVersion=string()),['referenceCaptureId','referenceContentRevision','movingCaptureId','movingContentRevision','algorithmVersion'])
add('ExportRequest',dict(patientIds=array(uid),includeImages=boolean),['patientIds','includeImages'])
add('CloseAccount',dict(password=string(format='password'),confirmation=enum('CLOSE_MY_ACCOUNT')),['password','confirmation'])
add('AlignmentResult',dict(referenceCaptureId=uid,movingCaptureId=uid,success=boolean,message=text,transform=dict(array(number),minItems=9,maxItems=9),inliers=integer(minimum=0),matches=integer(minimum=0),alignedImageFileId=dict(uid,nullable=True)),['referenceCaptureId','movingCaptureId','success','message','inliers','matches'])
add('Job',dict(id=uid,type=enum('DELETE_PATIENT','EXPORT','CLOSE_ACCOUNT','REANALYSIS','ALIGNMENT'),state=enum('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED'),progress=dict(number,minimum=0,maximum=1),resultFileIds=array(uid),resultCaptureIds=array(uid),analysisResult=nullable('Analysis'),alignmentResult=nullable('AlignmentResult'),error=nullable('Error'),createdAt=timestamp,updatedAt=timestamp),['id','type','state','createdAt','updatedAt'])
add('AuditEvent',dict(id=uid,actorId=uid,action=string(),resourceType=string(),resourceId=uid,requestId=string(),createdAt=timestamp),['id','actorId','action','createdAt'])
add('Capabilities',dict(apiVersion=string(),maxUploadBytes=integer(),acceptedMimeTypes=array(string()),multipartEnabled=boolean,maxSyncBatch=integer(),maxPageSize=integer(),cloudDraftsEnabled=boolean,serverAlgorithmsEnabled=boolean),['apiVersion','maxUploadBytes','acceptedMimeTypes','multipartEnabled','maxSyncBatch','maxPageSize','cloudDraftsEnabled','serverAlgorithmsEnabled'])
add('Health',dict(status=enum('UP','DEGRADED'),serverTime=timestamp),['status','serverTime'])

# group|priority|method|path|summary|body|response|access
catalog='''认证|P0|POST|/auth/login|账号密码登录|Login|Tokens|公开，限流；角色只作提示
认证|P0|POST|/auth/refresh|轮换刷新令牌|Refresh|Tokens|有效刷新令牌，检测重放
认证|P0|POST|/auth/logout|退出并撤销会话|Refresh|-|Bearer + 刷新令牌
认证|P0|GET|/auth/me|当前公开账号信息|-|Profile|本人
认证|P1|POST|/auth/challenges|申请注册或恢复验证码|ChallengeRequest|Challenge|公开，限流、防枚举
认证|P1|POST|/auth/register|注册患者账号|Register|Profile|公开，仅患者；验证码一次性消费
认证|P1|POST|/auth/password-resets|验证并重置密码|ResetPassword|-|公开，恢复凭证，撤销旧会话
账号|P0|PATCH|/users/me|修改显示名|ProfilePatch|Profile|本人，不允许自改角色/机构
账号|P0|POST|/users/me/password|修改密码|PasswordChange|-|本人，验证当前密码并撤销其他会话
账号|P1|GET|/users/me/sessions|查询本人会话|-|[SessionInfo]|本人
账号|P1|DELETE|/users/me/sessions/{sessionId}|撤销指定会话|-|-|本人
账号|P1|POST|/users/me/active-organization|切换活动机构|OrganizationSwitch|Tokens|本人且机构成员有效；重置同步上下文
账号|P1|POST|/users/me/closure|申请关闭账号|CloseAccount|Job|本人，重新验证；不直接抹除医疗记录
机构|P1|POST|/organizations|后台受控开通机构|OrganizationCreate|Organization|平台受控管理权限，非公开自助医院注册
机构|P1|GET|/organizations|查询可管理或所属机构|-|[Organization]|已授权范围
机构|P1|GET|/organizations/{organizationId}|机构详情|-|Organization|成员或机构管理权限
机构|P1|PATCH|/organizations/{organizationId}|修改或停用机构|OrganizationPatch|Organization|机构/平台管理权限
机构|P1|GET|/organizations/{organizationId}/members|机构成员列表|-|[Membership]|机构管理权限
机构|P1|POST|/organizations/{organizationId}/members|添加或邀请工作人员|MembershipCreate|Membership|机构管理权限，用户必须可信开通
机构|P1|PATCH|/organizations/{organizationId}/members/{memberId}|修改成员身份或停用|MembershipPatch|Membership|机构管理权限，防止自助越权
患者|P0|GET|/patients|授权患者分页列表|-|[Patient]|本人关联或病例授权；可按姓名过滤
患者|P0|POST|/patients|新建患者或确认归属后导入|PatientCreate|Patient|patient:create；导入须检查 importId
患者|P0|GET|/patients/{patientId}|患者详情|-|Patient|patient:read + 资源范围
患者|P0|PATCH|/patients/{patientId}|修改患者基本信息|PatientPatch|Patient|patient:write
患者|P0|DELETE|/patients/{patientId}|受控删除患者及子资源|-|-|patient:delete；子资源墓碑同事务，较大删除可202
患者|P1|GET|/patients/{patientId}/accounts|查询核验后的患者账号关联|-|[PatientAccount]|患者本人/授权病例管理者
患者|P1|POST|/patients/{patientId}/accounts|核验后关联本人或监护账号|PatientAccountCreate|PatientAccount|患者绑定管理权限 + 有效 verificationId
患者|P1|POST|/patients/{patientId}/account-link-verifications|受控申请病例账号绑定核验|LinkVerificationCreate|LinkVerification|patient:verify；核验材料引用可信院内记录，非客户端自证
患者|P1|GET|/account-link-verifications/{verificationId}|查询绑定核验状态|-|LinkVerification|申请人或核验员，限同病例范围
患者|P1|POST|/account-link-verifications/{verificationId}/decision|受控确认或拒绝绑定核验|LinkVerificationDecision|LinkVerification|patient:verify；独立核验员，按机构自审限制
患者|P1|DELETE|/patients/{patientId}/accounts/{linkId}|撤销账号关联|-|-|本人解绑或受控管理权限
授权|P1|GET|/patients/{patientId}/grants|查询病例授权|-|[Grant]|患者本人/授权管理者
授权|P1|POST|/patients/{patientId}/grants|授权机构或医生访问|GrantCreate|Grant|grant:create，不得授出超出自身范围的权限
授权|P1|DELETE|/patients/{patientId}/grants/{grantId}|撤销病例授权|-|-|grant:revoke；同步撤权墓碑
病灶|P0|GET|/patients/{patientId}/lesions|患者病灶列表|-|[Lesion]|lesion:read + 患者授权
病灶|P0|POST|/lesions|新建病灶|LesionCreate|Lesion|lesion:create + 患者授权
病灶|P0|GET|/lesions/{lesionId}|病灶详情|-|Lesion|lesion:read
病灶|P0|PATCH|/lesions/{lesionId}|修改部位和备注|LesionPatch|Lesion|lesion:write
病灶|P0|DELETE|/lesions/{lesionId}|删除病灶及采集墓碑|-|-|lesion:delete；父子变更原子
采集|P0|GET|/lesions/{lesionId}/captures|病灶历史记录分页|-|[Capture]|capture:read；按 capturedAt,id 稳定排序
采集|P0|POST|/captures|提交图片引用与标定分析快照|CaptureCreate|Capture|capture:create；文件READY且归属匹配
采集|P0|GET|/captures/{captureId}|完整采集详情|-|Capture|capture:read
采集|P0|PATCH|/captures/{captureId}|更新标注或算法结果|CapturePatch|Capture|capture:write；新revision，原审核失效
采集|P0|DELETE|/captures/{captureId}|删除采集并写墓碑|-|-|capture:delete；文件延迟清理
采集|P1|GET|/lesions/{lesionId}/trends|查询有效面积及色差趋势|-|[TrendPoint]|capture:read；无效/仅照片不补0
文件|P0|POST|/file-uploads|初始化单文件或分片上传|UploadCreate|Upload|file:create + 患者授权
文件|P0|GET|/file-uploads/{uploadId}|上传进度及已接受分片|-|Upload|上传发起者/同范围授权；过期410
文件|P0|POST|/file-uploads/{uploadId}/sign-single|续签同一单文件上传URL|-|UploadUrl|原上传发起者，原会话未过期且未进入校验
文件|P1|POST|/file-uploads/{uploadId}/sign-parts|申请指定分片签名URL|SignParts|PartUrls|上传发起者，段号/次数限额
文件|P0|POST|/file-uploads/{uploadId}/complete|完成上传并校验字节|UploadComplete|File|上传发起者；异步校验时202 Upload
文件|P0|DELETE|/file-uploads/{uploadId}|中止未完成上传|-|-|上传发起者；已提交文件不可中止
文件|P0|GET|/files/{fileId}|文件元信息，不返回本机路径|-|File|file:read + 所属病例授权
文件|P0|POST|/files/{fileId}/download-url|申请短期授权下载URL|-|DownloadUrl|file:read；每次重新校验病例权限
文件|P1|DELETE|/files/{fileId}|删除未被引用文件|-|-|file:delete；被采集/草稿引用返回409
草稿|P1|GET|/lesions/{lesionId}/drafts|跨设备草稿列表|-|[Draft]|draft:read；默认仅本人草稿
草稿|P1|POST|/drafts|创建云端草稿|DraftCreate|Draft|draft:create；文件已确认
草稿|P1|GET|/drafts/{draftId}|获取草稿编辑快照|-|Draft|草稿所有者/显式授权
草稿|P1|PATCH|/drafts/{draftId}|保存结构化编辑快照|DraftPatch|Draft|草稿写权限；版本冲突不可静默覆盖
草稿|P1|DELETE|/drafts/{draftId}|删除草稿|-|-|草稿删除权限
审核|P1|POST|/captures/{captureId}/review-submissions|提交指定版本审核|ReviewSubmit|Review|review:submit + 病例权限
审核|P1|GET|/captures/{captureId}/reviews|审核历史|-|[Review]|review:read + 病例权限
审核|P1|GET|/reviews|机构待审列表|-|[Review]|review:read；可筛选status=PENDING
审核|P1|POST|/reviews/{reviewId}/decision|批准或拒绝固定版本|ReviewDecision|Review|review:decide；检查机构/版本/自审策略
同步|P0|POST|/offline-imports|确认旧本机数据导入范围|ImportCreate|ImportPlan|已验证身份，用户已明确确认归属
同步|P0|POST|/sync/snapshots|建立一致性首次同步快照|-|Snapshot|当前账号和活动范围
同步|P0|GET|/sync/snapshots/{snapshotId}|分页读取快照|-|SnapshotPage|快照所有者与原授权范围
同步|P0|GET|/sync/changes|按提交水位增量拉取|-|Changes|cursor绑定身份/机构/授权版本
同步|P1|POST|/sync/push|批量幂等推送元数据变更|Push|PushResult|每条独立校验资源权限，不接收图片字节
任务|P1|POST|/exports|申请授权病例数据导出|ExportRequest|Job|export:create + 每个患者授权
任务|P2|POST|/captures/{captureId}/reanalysis-jobs|申请服务器重新分析|Reanalysis|Job|analysis:run；固定采集版本
任务|P2|POST|/alignment-jobs|申请服务器两图对齐|Alignment|Job|analysis:run；双方采集均可读，固定版本
任务|P0|GET|/jobs/{jobId}|查询本人可见异步任务|-|Job|任务所有者/授权管理者；含大批量删除任务
运维|P0|GET|/system/capabilities|协商上传和同步能力|-|Capabilities|已登录客户端
运维|P0|GET|/system/health|仅暴露最小健康状态|-|Health|公开或网关限制，不含配置及依赖密码
运维|P1|GET|/audit-events|查询受控审计事件|-|[AuditEvent]|audit:read，受机构/平台权限限制'''
rows=[line.split('|') for line in catalog.splitlines()]
# Merge Patch 对嵌套对象递归可选；数组是整体替换，保留其元素的完整校验。
def patch_schema(schema):
    if '$ref' in schema: return patch_schema(S[schema['$ref'].split('/')[-1]])
    out=dict(schema)
    if out.get('type')=='object':
        out.pop('required',None)
        out['properties']={k:patch_schema(v) for k,v in out.get('properties',{}).items()}
    return out
for name in list(S):
    if name.endswith('Patch'): S[name]=patch_schema(S[name])
paths={}; ids=set()
public={'/auth/login','/auth/refresh','/auth/challenges','/auth/register','/auth/password-resets','/system/health'}
readonlyposts={'/files/{fileId}/download-url'}
revisionposts={'/captures/{captureId}/review-submissions','/reviews/{reviewId}/decision','/account-link-verifications/{verificationId}/decision'}
for group,priority,method,path,title,body,response,access in rows:
    operation=re.sub(r'[^a-zA-Z0-9]+','_',method.lower()+'_'+path).strip('_')
    assert operation not in ids; ids.add(operation)
    op={'operationId':operation,'tags':[group],'summary':title,'description':access+'。详见 README.md 的校验、同步和权限约束。','x-priority':priority,'security':[] if path in public else [{'bearerAuth':[]}], 'parameters':[]}
    for name in re.findall(r'{(\w+)}',path): op['parameters'].append({'name':name,'in':'path','required':True,'schema':uid})
    is_list=response.startswith('[')
    if (is_list and method=='GET') or path.startswith('/sync/snapshots/'):
        op['parameters'] += [{'name':'pageToken','in':'query','schema':string()}, {'name':'limit','in':'query','schema':{'type':'integer','minimum':1,'maximum':100,'default':50}}]
    if path=='/sync/changes':
        op['parameters'] += [{'name':'cursor','in':'query','required':True,'schema':string()}, {'name':'limit','in':'query','schema':{'type':'integer','minimum':1,'maximum':100,'default':100}}]
    if method=='GET' and path=='/patients': op['parameters'].append({'name':'q','in':'query','schema':string(maxLength=128),'description':'在已授权患者范围内搜索姓名'})
    if method=='GET' and path=='/reviews': op['parameters'].append({'name':'status','in':'query','schema':enum('PENDING','APPROVED','REJECTED','VOID')})
    if method=='GET' and path in ['/lesions/{lesionId}/captures','/lesions/{lesionId}/trends','/audit-events']:
        op['parameters'] += [{'name':n,'in':'query','schema':timestamp,'description':'[fromAt,toAt)，服务端验证区间'} for n in ['fromAt','toAt']]
    if method=='PATCH' or (method=='DELETE' and path not in ['/file-uploads/{uploadId}','/users/me/sessions/{sessionId}','/files/{fileId}']) or path in revisionposts:
        op['parameters'].append({'name':'If-Match','in':'header','required':True,'schema':string(pattern='^"[1-9][0-9]*"$'),'description':'资源当前revision的ETag；审核decision使用Review的revision，同时校验captureContentRevision。'})
    idem=method=='POST' and path not in public and path not in readonlyposts and path!='/auth/logout'
    if idem: op['parameters'].append({'name':'Idempotency-Key','in':'header','required':True,'schema':string(minLength=1,maxLength=128)})
    if body!='-':
        mime='application/merge-patch+json' if method=='PATCH' else 'application/json'
        op['requestBody']={'required':True,'content':{mime:{'schema':ref(body)}}}
    if response=='-': code='204'; result=None
    elif response=='Job' and method=='POST': code='202'; result=ref('Job')
    else:
        code='201' if method=='POST' and path not in public and path not in readonlyposts and path not in revisionposts and path not in ['/users/me/active-organization','/sync/push','/file-uploads/{uploadId}/complete','/file-uploads/{uploadId}/sign-parts'] else '200'
        result=obj(dict(items=array(ref(response[1:-1])),nextPageToken=string(nullable=True)),['items','nextPageToken']) if is_list else ref(response)
    success={'description':'成功；授权和字段校验见总说明','headers':{'X-Request-Id':{'schema':string()}}}
    if result: success['content']={'application/json':{'schema':result}}
    if response in ('Profile','Patient','Lesion','Capture','Draft','Review','Organization','Membership','Grant','PatientAccount','LinkVerification'):
        success['headers']['ETag']={'schema':string(),'description':'带引号的revision'}
    op['responses']={code:success,'default':{'description':'标准错误，HTTP状态码见总说明','content':{'application/json':{'schema':ref('Error')}}}}
    if path=='/file-uploads/{uploadId}/complete': op['responses']['202']={'description':'文件校验仍进行中，轮询Upload直到READY','content':{'application/json':{'schema':ref('Upload')}}}
    if path=='/patients/{patientId}' and method=='DELETE': op['responses']['202']={'description':'大批量级联删除异步执行，期间禁止对子资源写入','content':{'application/json':{'schema':ref('Job')}}}
    paths.setdefault(path,{})[method.lower()]=op

spec={'openapi':'3.0.3','info':{'title':'白癜风随访 V1 后端接口建议合同','version':'1.0.0','description':'2026-11-03；对应App V1.6.0。设计合同，当前没有已部署后端。P0闭环/P1协作/P2算法。与README.md业务不变量共同使用。'},'servers':[{'url':'https://api.example.invalid/api/v1','description':'占位地址，实施时替换，当前不可用'}], 'tags':[{'name':g} for g in dict.fromkeys(r[0] for r in rows)],'paths':paths,'components':{'securitySchemes':{'bearerAuth':{'type':'http','scheme':'bearer','bearerFormat':'opaque-or-JWT'}},'schemas':S}}
root.joinpath('backend-v1.openapi.json').write_text(json.dumps(spec,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
md=['# 后端接口逐项清单','',f'共 {len(rows)} 个业务 HTTP 操作；不计对象存储签名 URL 上的实际 PUT/GET。P0/P1/P2 含义与业务约束见 [总说明](README.md)。所有路径前缀 `/api/v1`。','', '输入/输出类型名称对应 [OpenAPI schemas](backend-v1.openapi.json)。`—` 表示无请求正文或204无响应正文；`[Type]` 表示分页集合（items、nextPageToken），PartUrls 仅含 items，不分页。所有受限字段、父子权限和版本条件还需结合总说明执行。','']
for group in dict.fromkeys(r[0] for r in rows):
    md += ['## '+group,'','| 优先级 | 方法与路径 | 用途 | 请求 → 响应 | 授权/约束 |','|---|---|---|---|---|']
    for g,p,m,path,title,b,out,a in rows:
        if g==group: md.append(f'| {p} | `{m} {path}` | {title} | {b.replace("-","—")} → {out.replace("-","—")} | {a} |')
    md.append('')
md += ['## 写入条件与状态码','', 'PATCH/资源删除通常需 If-Match；准确的例外、审核版本条件、幂等键和成功响应码已写入 OpenAPI 各 operation。POST 不一概表示201，异步任务为202；批量推送200需逐项查看结果。文件 complete 200为READY、202为仍校验。','', '绑定患者账号需要可信 verificationId，由机构受控核验系统产生；当前合同不把认证短信 challenge 自动等同于患者病例绑定核验。实际启用前必须实现该核验流程或关闭绑定入口。','', '账号受控开通、平台机构开通可以先通过受限管理工具完成；本文不规定对公网开放的超级管理员注册/任意账号创建入口。','', '系统能力、分页上限和上传大小均由服务端约束；客户端参数不是可信限制。']
root.joinpath('api-catalog.md').write_text('\n'.join(md)+'\n',encoding='utf-8')
# 基本结构检查：不替代标准OpenAPI validator及实际服务端契约/权限测试。
def check(node):
    if isinstance(node,dict):
        if '$ref' in node:
            assert node['$ref'].startswith('#/components/schemas/')
            assert node['$ref'].split('/')[-1] in S, node['$ref']
        for v in node.values(): check(v)
    elif isinstance(node,list):
        for v in node: check(v)
check(spec)
for path, ops in paths.items():
    for op in ops.values():
        assert set(re.findall(r'{(\w+)}',path))=={p['name'] for p in op['parameters'] if p['in']=='path'}
        for name, schema in S.items(): assert set(schema.get('required',[])) <= schema.get('properties',{}).keys(), name
print(json.dumps({'operations':len(rows),'paths':len(paths),'schemas':len(S),'priorities':{p:sum(r[1]==p for r in rows) for p in ['P0','P1','P2']},'checks':'JSON/local refs/path params/required fields/operationId uniqueness passed'},ensure_ascii=False))
