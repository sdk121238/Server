# 前端联调字段字典（当前P0快照）

版本：2026-11-05 / API v1.0.0。与 [接口清单](frontend-api-alignment.md) 及 [OpenAPI快照](frontend-p0.openapi.json) 配套使用。

本表从当前P0导出Schema提取，必填/可空分开列出。“必填”指该对象存在时的合同要求；可选对象省略时，不要求其子字段。Patch嵌套字段按补丁DTO递归可选，但合并后的完整资源仍须通过验证。

下表包含请求与响应模型，不能把响应模型的所有字段回传给创建/补丁接口。UUID为字符串，int64时间为Unix毫秒；数组整体替换，未知字段禁止或依具体additionalProperties规则处理。

创建患者/病灶/采集：客户端生成新UUID。File/Upload/Snapshot/ImportPlan：服务器生成ID。Profile没有revision字段，版本从响应头ETag获取。

普通列表Page<T>＝`{"items":[T],"nextPageToken":string|null}`。一般limit默认50、最大100；增量默认100。DTO字段的空值不表示0或false。

实现约束补充：新密码至少12字符且UTF-8最多72字节；图像总像素<=40000000、宽高各<=20000。valid analysis还需尺度、几何和面积一致性校验。targetOrganizationId当前仅省略/null。更完整业务约束见主清单，Schema不能单独表达这些关系。

## Error

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `code` | string | 是 | 否 | — |
| `message` | string | 是 | 否 | — |
| `requestId` | string | 是 | 否 | — |
| `details` | object | 否 | 否 | — |

## Profile

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `id` | string (uuid) | 是 | 否 | — |
| `account` | string | 是 | 否 | — |
| `displayName` | string | 是 | 否 | — |
| `role` | string | 是 | 否 | 枚举=PATIENT |
| `organizationId` | string (uuid) | 是 | 是 | — |
| `permissions` | array<string> | 是 | 否 | — |

## Login

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `account` | string | 是 | 否 | 最短字符=1；最长字符=128 |
| `password` | string (password) | 是 | 否 | 最短字符=1 |
| `requestedRole` | string | 否 | 否 | 枚举=PATIENT, HOSPITAL_STAFF |

## Tokens

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `profile` | Profile | 是 | 否 | — |
| `accessToken` | string | 是 | 否 | — |
| `refreshToken` | string | 是 | 否 | — |
| `expiresAt` | integer (int64) | 是 | 否 | >=0 |

## Refresh

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `refreshToken` | string | 是 | 否 | 最短字符=1 |

## ProfilePatch

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `displayName` | string | 否 | 否 | 最短字符=1；最长字符=128 |

## PasswordChange

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `currentPassword` | string (password) | 是 | 否 | — |
| `newPassword` | string (password) | 是 | 否 | — |

## Patient

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `id` | string (uuid) | 是 | 否 | — |
| `revision` | integer (int64) | 是 | 否 | >=1 |
| `createdAt` | integer (int64) | 是 | 否 | >=0 |
| `updatedAt` | integer (int64) | 是 | 否 | >=0 |
| `deletedAt` | integer (int64) | 否 | 是 | >=0 |
| `name` | string | 是 | 否 | — |
| `birthDate` | string (date) | 是 | 是 | — |

## PatientCreate

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `id` | string (uuid) | 是 | 否 | — |
| `name` | string | 是 | 否 | 最短字符=1；最长字符=128 |
| `birthDate` | string (date) | 否 | 是 | — |
| `clientCreatedAt` | integer (int64) | 否 | 否 | >=0 |
| `importId` | string (uuid) | 否 | 否 | — |

## PatientPatch

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `name` | string | 否 | 否 | 最短字符=1；最长字符=128 |
| `birthDate` | string (date) | 否 | 是 | — |

## Lesion

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `id` | string (uuid) | 是 | 否 | — |
| `revision` | integer (int64) | 是 | 否 | >=1 |
| `createdAt` | integer (int64) | 是 | 否 | >=0 |
| `updatedAt` | integer (int64) | 是 | 否 | >=0 |
| `deletedAt` | integer (int64) | 否 | 是 | >=0 |
| `patientId` | string (uuid) | 是 | 否 | — |
| `bodySite` | string | 是 | 否 | — |
| `note` | string | 是 | 否 | 最长字符=2000 |

## LesionCreate

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `id` | string (uuid) | 是 | 否 | — |
| `patientId` | string (uuid) | 是 | 否 | — |
| `bodySite` | string | 是 | 否 | 最短字符=1；最长字符=128 |
| `note` | string | 否 | 否 | 最长字符=2000 |
| `clientCreatedAt` | integer (int64) | 否 | 否 | >=0 |
| `importId` | string (uuid) | 否 | 否 | — |

## LesionPatch

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `bodySite` | string | 否 | 否 | 最短字符=1；最长字符=128 |
| `note` | string | 否 | 否 | 最长字符=2000 |

## Point

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `x` | number (double) | 是 | 否 | >=0 |
| `y` | number (double) | 是 | 否 | >=0 |

## Lab

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `l` | number (double) | 是 | 否 | — |
| `a` | number (double) | 是 | 否 | — |
| `b` | number (double) | 是 | 否 | — |

## Rgb

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `r` | integer | 是 | 否 | >=0；<=255 |
| `g` | integer | 是 | 否 | >=0；<=255 |
| `b` | integer | 是 | 否 | >=0；<=255 |

## CaptureMetadata

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `appVersion` | string | 否 | 否 | — |
| `algorithmVersions` | object | 否 | 否 | 额外键的值类型=string |
| `collectedAt` | integer (int64) | 否 | 是 | >=0 |
| `importedAt` | integer (int64) | 否 | 是 | >=0 |
| `originalCapturedAt` | integer (int64) | 否 | 是 | >=0 |
| `liveQc` | object | 否 | 是 | 不接收未定义字段 |
| `liveQc.liveGateExecuted` | boolean | 否 | 是 | — |
| `liveQc.passed` | boolean | 否 | 是 | — |
| `liveQc.issues` | array<string> | 否 | 否 | — |
| `liveQc.sharpness` | number (double) | 否 | 是 | — |
| `liveQc.meanExposure` | number (double) | 否 | 是 | >=0；<=1 |
| `liveQc.clippedPixelRatio` | number (double) | 否 | 是 | >=0；<=1 |
| `liveQc.markerDetected` | boolean | 否 | 是 | — |
| `liveQc.targetSizeRatio` | number (double) | 否 | 是 | — |
| `liveQc.perspectiveRatio` | number (double) | 否 | 是 | — |

## CaptureCreate

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `id` | string (uuid) | 是 | 否 | — |
| `lesionId` | string (uuid) | 是 | 否 | — |
| `imageFileId` | string (uuid) | 是 | 否 | — |
| `capturedAt` | integer (int64) | 是 | 否 | >=0 |
| `deviceModel` | string | 是 | 否 | 最长字符=256 |
| `source` | string | 是 | 否 | 枚举=CAMERA, GALLERY, UNKNOWN |
| `assetKind` | string | 是 | 否 | 枚举=CAPTURE_IMAGE, ANALYSIS_COPY, UNKNOWN |
| `referenceOnly` | boolean | 是 | 是 | — |
| `qualityNote` | string | 否 | 是 | 最长字符=2000 |
| `calibration` | object | 否 | 是 | 不接收未定义字段 |
| `calibration.markerId` | string | 是 | 否 | — |
| `calibration.markerWidthMm` | number (double) | 是 | 否 | >0 |
| `calibration.pixelsPerMm` | number (double) | 是 | 否 | >0 |
| `calibration.qualityScore` | number (double) | 是 | 否 | >=0；<=1 |
| `calibration.colorReference` | array<Lab> | 否 | 否 | — |
| `calibration.swatchRgb` | array<Rgb> | 否 | 否 | — |
| `calibration.colorCorrection` | object | 否 | 是 | 不接收未定义字段 |
| `calibration.colorCorrection.redScale` | number (double) | 是 | 否 | — |
| `calibration.colorCorrection.redOffset` | number (double) | 是 | 否 | — |
| `calibration.colorCorrection.greenScale` | number (double) | 是 | 否 | — |
| `calibration.colorCorrection.greenOffset` | number (double) | 是 | 否 | — |
| `calibration.colorCorrection.blueScale` | number (double) | 是 | 否 | — |
| `calibration.colorCorrection.blueOffset` | number (double) | 是 | 否 | — |
| `calibration.method` | string | 否 | 否 | 枚举=AUTO_MARKER, MANUAL, UNKNOWN |
| `calibration.markerCorners` | array<Point> | 否 | 否 | — |
| `calibration.scalePoints` | array<Point> | 否 | 否 | — |
| `calibration.calibrationVersion` | string | 否 | 否 | — |
| `analysis` | object | 否 | 是 | 不接收未定义字段 |
| `analysis.areaMm2` | number (double) | 否 | 是 | >=0 |
| `analysis.lesionColor` | object | 否 | 是 | 不接收未定义字段 |
| `analysis.lesionColor.l` | number (double) | 是 | 否 | — |
| `analysis.lesionColor.a` | number (double) | 是 | 否 | — |
| `analysis.lesionColor.b` | number (double) | 是 | 否 | — |
| `analysis.colorDifference` | number (double) | 否 | 是 | >=0 |
| `analysis.algorithmVersion` | string | 是 | 否 | — |
| `analysis.isValid` | boolean | 是 | 否 | — |
| `analysis.failureReason` | string | 否 | 是 | — |
| `annotation` | object | 否 | 是 | 不接收未定义字段 |
| `annotation.points` | array<Point> | 是 | 否 | 最多项=2000 |
| `annotation.imageWidth` | integer (int64) | 是 | 否 | >=1 |
| `annotation.imageHeight` | integer (int64) | 是 | 否 | >=1 |
| `annotation.segmentationVersion` | string | 否 | 是 | — |
| `annotation.edited` | boolean | 是 | 否 | — |
| `metadata` | CaptureMetadata | 否 | 否 | — |
| `importId` | string (uuid) | 否 | 否 | — |

## Capture

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `id` | string (uuid) | 是 | 否 | — |
| `revision` | integer (int64) | 是 | 否 | >=1 |
| `createdAt` | integer (int64) | 是 | 否 | >=0 |
| `updatedAt` | integer (int64) | 是 | 否 | >=0 |
| `deletedAt` | integer (int64) | 否 | 是 | >=0 |
| `lesionId` | string (uuid) | 是 | 否 | — |
| `imageFileId` | string (uuid) | 是 | 否 | — |
| `capturedAt` | integer (int64) | 是 | 否 | >=0 |
| `deviceModel` | string | 否 | 否 | 最长字符=256 |
| `source` | string | 是 | 否 | 枚举=CAMERA, GALLERY, UNKNOWN |
| `assetKind` | string | 是 | 否 | 枚举=CAPTURE_IMAGE, ANALYSIS_COPY, UNKNOWN |
| `referenceOnly` | boolean | 是 | 是 | — |
| `qualityNote` | string | 否 | 是 | 最长字符=2000 |
| `calibration` | object | 否 | 是 | 不接收未定义字段 |
| `calibration.markerId` | string | 是 | 否 | — |
| `calibration.markerWidthMm` | number (double) | 是 | 否 | >0 |
| `calibration.pixelsPerMm` | number (double) | 是 | 否 | >0 |
| `calibration.qualityScore` | number (double) | 是 | 否 | >=0；<=1 |
| `calibration.colorReference` | array<Lab> | 否 | 否 | — |
| `calibration.swatchRgb` | array<Rgb> | 否 | 否 | — |
| `calibration.colorCorrection` | object | 否 | 是 | 不接收未定义字段 |
| `calibration.colorCorrection.redScale` | number (double) | 是 | 否 | — |
| `calibration.colorCorrection.redOffset` | number (double) | 是 | 否 | — |
| `calibration.colorCorrection.greenScale` | number (double) | 是 | 否 | — |
| `calibration.colorCorrection.greenOffset` | number (double) | 是 | 否 | — |
| `calibration.colorCorrection.blueScale` | number (double) | 是 | 否 | — |
| `calibration.colorCorrection.blueOffset` | number (double) | 是 | 否 | — |
| `calibration.method` | string | 否 | 否 | 枚举=AUTO_MARKER, MANUAL, UNKNOWN |
| `calibration.markerCorners` | array<Point> | 否 | 否 | — |
| `calibration.scalePoints` | array<Point> | 否 | 否 | — |
| `calibration.calibrationVersion` | string | 否 | 否 | — |
| `analysis` | object | 否 | 是 | 不接收未定义字段 |
| `analysis.areaMm2` | number (double) | 否 | 是 | >=0 |
| `analysis.lesionColor` | object | 否 | 是 | 不接收未定义字段 |
| `analysis.lesionColor.l` | number (double) | 是 | 否 | — |
| `analysis.lesionColor.a` | number (double) | 是 | 否 | — |
| `analysis.lesionColor.b` | number (double) | 是 | 否 | — |
| `analysis.colorDifference` | number (double) | 否 | 是 | >=0 |
| `analysis.algorithmVersion` | string | 是 | 否 | — |
| `analysis.isValid` | boolean | 是 | 否 | — |
| `analysis.failureReason` | string | 否 | 是 | — |
| `annotation` | object | 否 | 是 | 不接收未定义字段 |
| `annotation.points` | array<Point> | 是 | 否 | 最多项=2000 |
| `annotation.imageWidth` | integer (int64) | 是 | 否 | >=1 |
| `annotation.imageHeight` | integer (int64) | 是 | 否 | >=1 |
| `annotation.segmentationVersion` | string | 否 | 是 | — |
| `annotation.edited` | boolean | 是 | 否 | — |
| `metadata` | CaptureMetadata | 否 | 否 | — |
| `reviewStatus` | string | 是 | 否 | 枚举=NOT_SUBMITTED |
| `contentRevision` | integer (int64) | 是 | 否 | >=1 |

## CapturePatch

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `qualityNote` | string | 否 | 是 | 最长字符=2000 |
| `calibration` | object | 否 | 是 | 不接收未定义字段 |
| `calibration.markerId` | string | 否 | 否 | — |
| `calibration.markerWidthMm` | number (double) | 否 | 否 | >0 |
| `calibration.pixelsPerMm` | number (double) | 否 | 否 | >0 |
| `calibration.qualityScore` | number (double) | 否 | 否 | >=0；<=1 |
| `calibration.colorReference` | array<Lab> | 否 | 否 | — |
| `calibration.swatchRgb` | array<Rgb> | 否 | 否 | — |
| `calibration.colorCorrection` | object | 否 | 是 | 不接收未定义字段 |
| `calibration.colorCorrection.redScale` | number (double) | 否 | 否 | — |
| `calibration.colorCorrection.redOffset` | number (double) | 否 | 否 | — |
| `calibration.colorCorrection.greenScale` | number (double) | 否 | 否 | — |
| `calibration.colorCorrection.greenOffset` | number (double) | 否 | 否 | — |
| `calibration.colorCorrection.blueScale` | number (double) | 否 | 否 | — |
| `calibration.colorCorrection.blueOffset` | number (double) | 否 | 否 | — |
| `calibration.method` | string | 否 | 否 | 枚举=AUTO_MARKER, MANUAL, UNKNOWN |
| `calibration.markerCorners` | array<Point> | 否 | 否 | — |
| `calibration.scalePoints` | array<Point> | 否 | 否 | — |
| `calibration.calibrationVersion` | string | 否 | 否 | — |
| `analysis` | object | 否 | 是 | 不接收未定义字段 |
| `analysis.areaMm2` | number (double) | 否 | 是 | >=0 |
| `analysis.lesionColor` | object | 否 | 是 | 不接收未定义字段 |
| `analysis.lesionColor.l` | number (double) | 否 | 否 | — |
| `analysis.lesionColor.a` | number (double) | 否 | 否 | — |
| `analysis.lesionColor.b` | number (double) | 否 | 否 | — |
| `analysis.colorDifference` | number (double) | 否 | 是 | >=0 |
| `analysis.algorithmVersion` | string | 否 | 否 | — |
| `analysis.isValid` | boolean | 否 | 否 | — |
| `analysis.failureReason` | string | 否 | 是 | — |
| `annotation` | object | 否 | 是 | 不接收未定义字段 |
| `annotation.points` | array<Point> | 否 | 否 | 最多项=2000 |
| `annotation.imageWidth` | integer (int64) | 否 | 否 | >=1 |
| `annotation.imageHeight` | integer (int64) | 否 | 否 | >=1 |
| `annotation.segmentationVersion` | string | 否 | 是 | — |
| `annotation.edited` | boolean | 否 | 否 | — |
| `metadata` | object | 否 | 否 | 不接收未定义字段 |
| `metadata.appVersion` | string | 否 | 否 | — |
| `metadata.algorithmVersions` | object | 否 | 否 | 额外键的值类型=string |
| `metadata.collectedAt` | integer (int64) | 否 | 是 | >=0 |
| `metadata.importedAt` | integer (int64) | 否 | 是 | >=0 |
| `metadata.originalCapturedAt` | integer (int64) | 否 | 是 | >=0 |
| `metadata.liveQc` | object | 否 | 是 | 不接收未定义字段 |
| `metadata.liveQc.liveGateExecuted` | boolean | 否 | 是 | — |
| `metadata.liveQc.passed` | boolean | 否 | 是 | — |
| `metadata.liveQc.issues` | array<string> | 否 | 否 | — |
| `metadata.liveQc.sharpness` | number (double) | 否 | 是 | — |
| `metadata.liveQc.meanExposure` | number (double) | 否 | 是 | >=0；<=1 |
| `metadata.liveQc.clippedPixelRatio` | number (double) | 否 | 是 | >=0；<=1 |
| `metadata.liveQc.markerDetected` | boolean | 否 | 是 | — |
| `metadata.liveQc.targetSizeRatio` | number (double) | 否 | 是 | — |
| `metadata.liveQc.perspectiveRatio` | number (double) | 否 | 是 | — |

## File

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `id` | string (uuid) | 是 | 否 | — |
| `kind` | string | 是 | 否 | 枚举=IMAGE |
| `sizeBytes` | integer (int64) | 是 | 否 | >=1 |
| `sha256` | string | 是 | 否 | 格式=^[a-f0-9]{64}$ |
| `mimeType` | string | 是 | 否 | 枚举=image/jpeg, image/png |
| `width` | integer (int64) | 是 | 是 | >=1 |
| `height` | integer (int64) | 是 | 是 | >=1 |
| `state` | string | 是 | 否 | 枚举=PENDING, READY |
| `createdAt` | integer (int64) | 否 | 否 | >=0 |

## UploadCreate

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `patientId` | string (uuid) | 是 | 否 | — |
| `sizeBytes` | integer (int64) | 是 | 否 | >=1；<=104857600 |
| `sha256` | string | 是 | 否 | 格式=^[a-f0-9]{64}$ |
| `mimeType` | string | 是 | 否 | 枚举=image/jpeg, image/png |
| `width` | integer (int64) | 是 | 否 | >=1 |
| `height` | integer (int64) | 是 | 否 | >=1 |
| `strategy` | string | 是 | 否 | 枚举=SINGLE |

## Part

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `partNumber` | integer (int64) | 是 | 否 | >=1 |
| `etag` | string | 是 | 否 | — |
| `sha256` | string | 是 | 否 | 格式=^[a-f0-9]{64}$ |

## Upload

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `id` | string (uuid) | 是 | 否 | — |
| `fileId` | string (uuid) | 是 | 否 | — |
| `strategy` | string | 是 | 否 | 枚举=SINGLE |
| `state` | string | 是 | 否 | 枚举=PENDING, UPLOADING, READY, ABORTED |
| `uploadUrl` | string (uri) | 否 | 是 | — |
| `requiredHeaders` | object | 是 | 否 | 额外键的值类型=string |
| `expiresAt` | integer (int64) | 是 | 否 | >=0 |
| `partSizeBytes` | integer (int64) | 否 | 否 | >=1 |
| `maxParts` | integer (int64) | 否 | 否 | >=1 |
| `acceptedParts` | array<Part> | 否 | 否 | — |
| `error` | object | 否 | 是 | 不接收未定义字段 |
| `error.code` | string | 是 | 否 | — |
| `error.message` | string | 是 | 否 | — |
| `error.requestId` | string | 是 | 否 | — |
| `error.details` | object | 否 | 否 | — |
| `urlExpiresAt` | integer (int64) | 否 | 是 | >=0 |

## UploadComplete

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `parts` | array<Part> | 否 | 否 | 最多项=0 |

## UploadUrl

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `url` | string (uri) | 是 | 否 | — |
| `urlExpiresAt` | integer (int64) | 是 | 否 | >=0 |
| `requiredHeaders` | object | 是 | 否 | 额外键的值类型=string |

## DownloadUrl

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `url` | string (uri) | 是 | 否 | — |
| `expiresAt` | integer (int64) | 是 | 否 | >=0 |
| `sha256` | string | 是 | 否 | — |
| `sizeBytes` | integer (int64) | 是 | 否 | — |
| `mimeType` | string | 是 | 否 | — |

## ImportCreate

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `clientDatasetId` | string (uuid) | 是 | 否 | — |
| `confirmedPatientIds` | array<string (uuid)> | 是 | 否 | 最少项=1；最多项=100 |
| `targetOrganizationId` | string (uuid) | 否 | 是 | 当前个人工作区仅省略或null；非空机构ID返回403。 |

## ImportPlan

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `id` | string (uuid) | 是 | 否 | — |
| `allowedPatientIds` | array<string (uuid)> | 是 | 否 | — |
| `expiresAt` | integer (int64) | 是 | 否 | >=0 |

## Snapshot

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `id` | string (uuid) | 是 | 否 | — |
| `expiresAt` | integer (int64) | 是 | 否 | >=0 |

## Change

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `entityType` | string | 是 | 否 | 枚举=PATIENT, LESION, CAPTURE |
| `entityId` | string (uuid) | 是 | 否 | — |
| `operation` | string | 是 | 否 | 枚举=UPSERT, DELETE |
| `revision` | integer (int64) | 是 | 否 | >=1 |
| `payload` | object | 是 | 是 | UPSERT 为对应 entityType 的完整资源；DELETE/ACCESS_REVOKED 为 null。 |

## SnapshotPage

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `snapshotId` | string (uuid) | 是 | 否 | — |
| `items` | array<Change> | 是 | 否 | — |
| `nextPageToken` | string | 是 | 是 | — |
| `nextCursor` | string | 是 | 是 | — |

## Changes

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `items` | array<Change> | 是 | 否 | — |
| `nextCursor` | string | 是 | 否 | — |
| `hasMore` | boolean | 是 | 否 | — |

## Capabilities

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `apiVersion` | string | 是 | 否 | — |
| `maxUploadBytes` | integer (int64) | 是 | 否 | — |
| `acceptedMimeTypes` | array<string> | 是 | 否 | — |
| `multipartEnabled` | boolean | 是 | 否 | — |
| `maxSyncBatch` | integer (int64) | 是 | 否 | — |
| `maxPageSize` | integer (int64) | 是 | 否 | — |
| `cloudDraftsEnabled` | boolean | 是 | 否 | — |
| `serverAlgorithmsEnabled` | boolean | 是 | 否 | — |

## Health

| 字段路径 | 类型 | 必填 | 允许null | 限制与说明 |
|---|---|---|---|---|
| `status` | string | 是 | 否 | 枚举=UP, DEGRADED |
| `serverTime` | integer (int64) | 是 | 否 | >=0 |
