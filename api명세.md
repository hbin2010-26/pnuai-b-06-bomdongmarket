# 백엔드1 (인증)

### **1.1. 이메일 인증번호 발송**

회원가입 전 단계로, 입력한 이메일 주소에 6자리 인증번호를 메일로 보낸다.

```
POST /auth/email/send-code
```

**Headers**: 불필요 (계정이 만들어지기 전 단계라 토큰이 있을 수 없다)

```
Content-Type: application/json
```

**Request Body**

```json
{
  "email": "farmer@example.com"
}
```

**필드 명세**

| 필드 | 타입 | 필수 | 제약 / 설명 |
| --- | --- | --- | --- |
| email | string | ✅ | 이메일 형식. 이미 가입된 주소면 409 |

**Response** `200 OK`

```json
{
  "success": true,
  "message": "인증번호를 보냈습니다. 메일함을 확인해 주세요.",
  "data": {
    "expiresInSeconds": 300,
    "resendAfterSeconds": 60
  }
}
```

`expiresInSeconds`는 인증번호 유효 시간, `resendAfterSeconds`는 재발송이 가능해질 때까지 남은 시간이다. 서버 설정으로 조정될 수 있으므로 클라이언트 타이머는 이 값을 쓴다.

**에러**

| 상태 | errorCode | 설명 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 이메일 누락 / 형식 오류 |
| 409 | `DUPLICATE_EMAIL` | 이미 사용 중인 이메일 |
| 429 | `EMAIL_VERIFICATION_TOO_FREQUENT` | 재발송 쿨다운(기본 60초)이 지나지 않음 |
| 502 | `EMAIL_VERIFICATION_SEND_FAILED` | SMTP 발송 실패 (이 경우 쿨다운은 시작되지 않는다) |

---

### **1.2. 이메일 인증번호 확인**

발송한 인증번호와 사용자가 입력한 값을 대조해 이메일 소유를 확인한다.

```
POST /auth/email/verify-code
```

**Headers**: 불필요

```
Content-Type: application/json
```

**Request Body**

```json
{
  "email": "farmer@example.com",
  "code": "123456"
}
```

**필드 명세**

| 필드 | 타입 | 필수 | 제약 / 설명 |
| --- | --- | --- | --- |
| email | string | ✅ | 1.1에서 인증번호를 발송한 주소 |
| code | string | ✅ | 6자리 숫자 |

**Response** `200 OK`

```json
{
  "success": true,
  "message": "이메일 인증이 완료되었습니다."
}
```

인증 성공 시각이 서버에 기록되고, 그 시점부터 기본 30분 안에 회원가입(1.3)을 마쳐야 한다. 인증번호를 재발송하면 이전 인증은 무효가 된다.

**에러**

| 상태 | errorCode | 설명 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 이메일 형식 오류 / 인증번호가 6자리 숫자가 아님 |
| 400 | `EMAIL_VERIFICATION_EXPIRED` | 인증번호 만료 (발송 이력이 아예 없는 경우도 포함) |
| 400 | `EMAIL_VERIFICATION_CODE_MISMATCH` | 인증번호 불일치 |
| 429 | `EMAIL_VERIFICATION_ATTEMPT_EXCEEDED` | 시도 횟수(기본 5회) 초과 — 재발송해야 풀린다 |

---

### **1.3. 회원가입**

이메일 인증을 마친 사용자가 계정을 만든다. 신규 회원은 소비자(`CONSUMER`)로 시작하고, 공간을 등록하면 `OWNER`, 매칭이 수락되면 `FARMER` 역할이 서버에서 더해진다.

```
POST /auth/signup
```

**Headers**: 불필요

```
Content-Type: application/json
```

**Request Body**

```json
{
  "email": "farmer@example.com",
  "password": "password123",
  "nickname": "도시농부"
}
```

**필드 명세**

| 필드 | 타입 | 필수 | 제약 / 설명 |
| --- | --- | --- | --- |
| email | string | ✅ | 이메일 형식. 1.2로 인증을 마친 주소여야 한다 |
| password | string | ✅ | 8자 이상 |
| nickname | string | ✅ | 2~30자 |

역할은 요청으로 받지 않는다. 인증 여부는 서버가 다시 확인하므로 프론트를 우회해 이 API를 직접 호출해도 인증 없이는 가입되지 않는다. 가입에 성공하면 해당 인증 기록은 소모되어 같은 인증으로 두 번 가입할 수 없다.

**Response** `201 Created`

```json
{
  "success": true,
  "message": "회원가입이 완료되었습니다.",
  "data": {
    "userId": 1,
    "email": "farmer@example.com",
    "nickname": "도시농부",
    "roles": ["CONSUMER"]
  }
}
```

**에러**

| 상태 | errorCode | 설명 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 필수값 누락 / 검증 실패 |
| 400 | `EMAIL_NOT_VERIFIED` | 이메일 인증을 마치지 않았거나 인증 유효시간(기본 30분)이 지남 |
| 409 | `DUPLICATE_EMAIL` | 이미 사용 중인 이메일 |

---

# 백엔드2 (공간)

### **2.1. 공간 등록**

공실 제공자(OWNER)가 스마트팜 전환 가능한 공간을 등록한다.

```
POST /spaces
```

**Headers**

```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**권한**: `OWNER` 역할만 등록 가능. 등록자는 인증 토큰에서 식별하며, `ownerId`는 요청 body로 받지 않는다.

**Request Body**

```json
{
  "title": "부산대 앞 20평 공실",
  "address": "부산광역시 금정구 장전동",
  "area": 66.0,
  "monthlyRent": 500000,
  "floor": 2,
  "hasWater": true,
  "hasElectricity": true,
  "hasVentilation": true,
  "description": "채광이 좋고 수도 사용이 가능한 상가 공실입니다.",
  "imageUrls": [
    "https://example.com/space-1.jpg",
    "https://example.com/space-2.jpg"
  ]
}
```

**필드 명세**

| 필드 | 타입 | 필수 | 제약 / 설명 |
| --- | --- | --- | --- |
| title | string | ✅ | 1~100자 |
| address | string | ✅ | 1~255자 |
| area | number | ✅ | > 0 (㎡) |
| monthlyRent | integer | ✅ | >= 0 (원/월) |
| floor | integer | ✅ | 층수 |
| hasWater | boolean | ✅ | 수도 가능 여부 |
| hasElectricity | boolean | ✅ | 전기 가능 여부 |
| hasVentilation | boolean | ✅ | 환기 가능 여부 |
| description | string | ❌ | 상세 설명 |
| imageUrls | string[] | ❌ | 이미지 URL 배열. 배열 순서가 노출 순서(0번이 대표 이미지). 미입력 시 빈 배열 |

**Response** `201 Created`

```json
{
  "success": true,
  "message": "공간 등록이 완료되었습니다.",
  "data": {
    "spaceId": 1,
    "title": "부산대 앞 20평 공실",
    "address": "부산광역시 금정구 장전동",
    "area": 66.0,
    "monthlyRent": 500000,
    "floor": 2,
    "hasWater": true,
    "hasElectricity": true,
    "hasVentilation": true,
    "description": "채광이 좋고 수도 사용이 가능한 상가 공실입니다.",
    "imageUrls": [
      "https://example.com/space-1.jpg",
      "https://example.com/space-2.jpg"
    ],
    "status": "AVAILABLE",
    "ownerId": 1,
    "createdAt": "2026-06-29T15:00:00"
  }
}
```

**에러**

| 상태 | errorCode | 설명 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 필수값 누락 / 검증 실패 |
| 401 | `UNAUTHORIZED` | 토큰 없음/만료 |
| 403 | `FORBIDDEN_ROLE` | OWNER가 아닌 사용자의 등록 시도 |

### **2.2. 공간 목록 조회 (검색 / 필터 / 정렬)**

등록된 공간 목록을 조회한다. 기본적으로 `deleted=false` 이고 `status=AVAILABLE` 인 공간만 노출한다.

```
GET /spaces
```

**Headers**: 불필요 (비로그인 조회 허용)

**Query Parameters** 

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| keyword | string | ❌ | — | 지역명(address) 또는 제목(title) 부분 검색 |
| minArea | number | ❌ | — | 최소 면적(㎡) |
| maxRent | number | ❌ | — | 최대 월세(원) |
| sort | string | ❌ | `latest` | 정렬 기준: `latest`(최신순) / `area`(면적 큰 순) / `rent`(월세 낮은 순) |
| page | integer | ❌ | 0 | 페이지 번호 (0부터) |
| size | integer | ❌ | 10 | 페이지 크기 |

**정렬 기준 상세**

| sort 값 | 정렬 규칙 |
| --- | --- |
| `latest` | `created_at DESC` (기본값) |
| `area` | `area DESC` (넓은 공간 우선) |
| `rent` | `monthly_rent ASC` (저렴한 월세 우선) |

**Example**

```
GET /spaces?keyword=부산&minArea=30&maxRent=700000&sort=rent&page=0&size=10
```

**Response** `200 OK`

목록 항목은 카드 UI용 요약 필드만 반환한다. `imageUrl`은 대표 이미지(sort_order=0) 1장.

```json
{
  "success": true,
  "message": "공간 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "spaceId": 1,
        "title": "부산대 앞 20평 공실",
        "address": "부산광역시 금정구 장전동",
        "area": 66.0,
        "monthlyRent": 500000,
        "status": "AVAILABLE",
        "imageUrl": "https://example.com/space-1.jpg"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

**에러**

| 상태 | errorCode | 설명 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 잘못된 정렬값 / 음수 페이지 등 |

---

### **2.3. 공간 상세 조회**

특정 공간의 전체 정보를 조회한다.

```
GET /spaces/{spaceId}
```

**Headers**: 불필요 (비로그인 조회 허용)

**Path Variable**

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| spaceId | integer | 조회할 공간 ID |

**Response** `200 OK`

상세 조회는 전체 이미지 배열(`imageUrls`)과 등록자(owner) 요약 정보를 포함한다.

```json
{
  "success": true,
  "message": "공간 상세 조회에 성공했습니다.",
  "data": {
    "spaceId": 1,
    "title": "부산대 앞 20평 공실",
    "address": "부산광역시 금정구 장전동",
    "area": 66.0,
    "monthlyRent": 500000,
    "floor": 2,
    "hasWater": true,
    "hasElectricity": true,
    "hasVentilation": true,
    "description": "채광이 좋고 수도 사용이 가능한 상가 공실입니다.",
    "imageUrls": [
      "https://example.com/space-1.jpg",
      "https://example.com/space-2.jpg"
    ],
    "status": "AVAILABLE",
    "owner": {
      "userId": 1,
      "nickname": "공간제공자1"
    },
    "createdAt": "2026-06-29T15:00:00",
    "updatedAt": "2026-06-29T15:00:00"
  }
}
```

**에러**

| 상태 | errorCode | 설명 |
| --- | --- | --- |
| 404 | `SPACE_NOT_FOUND` | 존재하지 않거나 삭제된(`deleted=true`) 공간 |

---

### **2.4. 내가 등록한 공간 조회**

로그인한 OWNER가 본인이 등록한 공간 목록을 조회한다. 본인 소유이므로 `status`와 무관하게(AVAILABLE/MATCHED/CLOSED 모두) 노출하되, Soft Delete된 공간은 제외한다.

```
GET /spaces/my
```

**Headers**

```
Authorization: Bearer {accessToken}
```

**권한**: 로그인 사용자 본인의 공간만 반환 (`owner_id` = 인증 사용자).

**Response** `200 OK`

```json
{
  "success": true,
  "message": "내 공간 목록 조회에 성공했습니다.",
  "data": [
    {
      "spaceId": 1,
      "title": "부산대 앞 20평 공실",
      "address": "부산광역시 금정구 장전동",
      "area": 66.0,
      "monthlyRent": 500000,
      "status": "AVAILABLE",
      "imageUrl": "https://example.com/space-1.jpg"
    }
  ]
}
```

> 페이지네이션 없이 전체 리스트 반환 (개인 등록 건수가 적은 MVP 기준). 향후 건수 증가 시 `GET /spaces`와 동일한 페이징 구조로 확장 가능.
> 

**에러**

| 상태 | errorCode | 설명 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 토큰 없음/만료 |

---

### **2.5. 공간 수정**

등록자(OWNER)가 본인 공간 정보를 수정한다. **부분 수정(Partial Update)** — 요청 body에 포함된 필드만 변경한다.

```
PATCH /spaces/{spaceId}
```

**Headers**

```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**권한**: 해당 공간의 등록자(`owner_id` = 인증 사용자)만 수정 가능.

**Path Variable**

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| spaceId | integer | 수정할 공간 ID |

**Request Body** (모든 필드 선택적, 보낸 필드만 반영)

```json
{
  "title": "부산대 앞 20평 공실 (가격 인하)",
  "monthlyRent": 450000,
  "description": "월세를 인하했습니다.",
  "status": "CLOSED",
  "imageUrls": [
    "https://example.com/space-1.jpg"
  ]
}
```

**필드 명세**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| title / address / area / monthlyRent / floor | — | 2.1과 동일 검증 규칙 |
| hasWater / hasElectricity / hasVentilation | boolean | 시설 정보 |
| description | string | 상세 설명 |
| status | string | `AVAILABLE` / `CLOSED` 직접 전환 가능. `MATCHED`는 매칭 도메인이 전환하므로 OWNER 직접 설정 불가(요청 시 `INVALID_STATUS_CHANGE`) |
| imageUrls | string[] | **전체 교체(replace)** 방식. 보낸 배열로 이미지 목록을 통째로 갱신(기존 이미지 삭제 후 재등록). 미포함 시 이미지 변경 없음 |

**Response** `200 OK`

```json
{
  "success": true,
  "message": "공간 정보가 수정되었습니다.",
  "data": {
    "spaceId": 1,
    "title": "부산대 앞 20평 공실 (가격 인하)",
    "address": "부산광역시 금정구 장전동",
    "area": 66.0,
    "monthlyRent": 450000,
    "floor": 2,
    "hasWater": true,
    "hasElectricity": true,
    "hasVentilation": true,
    "description": "월세를 인하했습니다.",
    "imageUrls": [
      "https://example.com/space-1.jpg"
    ],
    "status": "CLOSED",
    "ownerId": 1,
    "updatedAt": "2026-06-29T16:30:00"
  }
}
```

**에러**

| 상태 | errorCode | 설명 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 검증 실패 |
| 400 | `INVALID_STATUS_CHANGE` | `MATCHED`로의 직접 전환 등 허용되지 않는 상태 변경 |
| 401 | `UNAUTHORIZED` | 토큰 없음/만료 |
| 403 | `NOT_SPACE_OWNER` | 본인 공간이 아님 |
| 404 | `SPACE_NOT_FOUND` | 존재하지 않거나 삭제된 공간 |

---

### **2.6. 공간 삭제 (Soft Delete)**

등록자(OWNER)가 본인 공간을 삭제한다. 물리 삭제가 아닌 **논리 삭제**(`deleted=true`)로 처리하며, 삭제된 공간은 모든 조회 API에서 제외된다.

```
DELETE /spaces/{spaceId}
```

**Headers**

```
Authorization: Bearer {accessToken}
```

**권한**: 해당 공간의 등록자(`owner_id` = 인증 사용자)만 삭제 가능.

**Path Variable**

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| spaceId | integer | 삭제할 공간 ID |

**Response** `200 OK`

```json
{
  "success": true,
  "message": "공간이 삭제되었습니다.",
  "data": {
    "spaceId": 1,
    "deleted": true
  }
}
```

**에러**

| 상태 | errorCode | 설명 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 토큰 없음/만료 |
| 403 | `NOT_SPACE_OWNER` | 본인 공간이 아님 |
| 404 | `SPACE_NOT_FOUND` | 존재하지 않거나 이미 삭제된 공간 |

> **연동 주의**: 진행 중인(`REQUESTED`/`ACCEPTED`) 매칭이 있는 공간의 삭제 정책은 매칭 도메인(백엔드3)과 협의 필요. MVP 기준에서는 Soft Delete만 수행하고, 매칭 측에서 `deleted` 공간을 필터링하는 것으로 충분.
>

---

# 파일 (이미지 업로드)

공간 사진과 도면을 서버에 올리고 다시 내려받는 API. 공간 도메인은 여전히 **URL 문자열만** 저장하므로(`space_images` / `space_floor_plans`), 등록·수정 API에 파일을 직접 보내지 않고 **먼저 업로드해 URL을 받은 뒤 그 URL을 넣는 2단계 흐름**을 사용한다.

```
[1] POST /files   → 이미지 업로드, url 배열 수신
[2] POST /spaces  → 받은 url을 imageUrls / floorPlanUrls에 담아 등록
```

### **3.1. 이미지 업로드**

공간 사진 또는 도면 이미지를 한 번에 최대 10장까지 업로드한다.

```
POST /files
```

**Headers**

```
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

**권한**: 로그인 필요. 역할 제한은 없다(등록 준비 단계에서 누구나 업로드 가능).

**Request Body** (`multipart/form-data`)

| 파트 이름 | 타입 | 필수 | 제약 / 설명 |
| --- | --- | --- | --- |
| files | file[] | ✅ | 이미지 파일. 1~10개. 같은 파트 이름을 반복해 여러 장 전송 |

**파일 제약**

| 항목 | 값 |
| --- | --- |
| 허용 확장자 | `jpg` / `jpeg` / `png` / `webp` / `gif` |
| 장당 최대 크기 | 5MB |
| 요청 전체 최대 크기 | 55MB |
| 최대 장수 | 10장 / 요청 |

> **확장자 판정 기준**: 클라이언트가 보낸 `Content-Type`은 위조 가능하므로 신뢰하지 않고 **파일명 확장자**로 판정한다. `Content-Type: image/jpeg`로 보낸 `payload.svg`는 거부된다.

> **저장 파일명**: 원본 파일명을 그대로 쓰지 않고 서버가 UUID(하이픈 제거 32자리 hex)로 새로 만든다. 경로 조작(`../`)과 기존 파일 덮어쓰기를 차단하기 위함이며, 원본 이름은 응답의 `originalName`으로만 돌려준다.

**Example**

```bash
curl -X POST http://localhost:8080/api/files \
  -H "Authorization: Bearer {accessToken}" \
  -F "files=@공실-정면.jpg" \
  -F "files=@공실-내부.jpg"
```

**Response** `200 OK`

배열 순서는 요청에 보낸 순서와 동일하다.

```json
{
  "success": true,
  "message": "이미지 업로드가 완료되었습니다.",
  "data": [
    {
      "url": "http://localhost:8080/api/files/0505ce6a52134905a2c28b5621c2d4e2.jpg",
      "originalName": "공실-정면.jpg",
      "size": 482913
    },
    {
      "url": "http://localhost:8080/api/files/5fd13608f187465dbda0ee0df1aea927.jpg",
      "originalName": "공실-내부.jpg",
      "size": 331204
    }
  ]
}
```

**필드 명세 (data 배열 항목)**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| url | string | 이미지 공개 URL. `imageUrls` / `floorPlanUrls`에 그대로 넣는다 |
| originalName | string | 업로드한 원본 파일명 |
| size | integer | 파일 크기(byte) |

**에러**

| 상태 | errorCode | 설명 |
| --- | --- | --- |
| 400 | `FILE_EMPTY` | 파일 파트가 없거나 빈 파일 |
| 400 | `FILE_TYPE_NOT_SUPPORTED` | 허용하지 않는 확장자 |
| 400 | `FILE_COUNT_EXCEEDED` | 한 요청에 10장 초과 |
| 401 | `UNAUTHORIZED` | 토큰 없음/만료 |
| 413 | `FILE_TOO_LARGE` | 장당 5MB 초과 |
| 500 | `FILE_STORAGE_FAILED` | 서버 디스크 저장 실패 |

---

### **3.2. 업로드 이미지 조회**

업로드된 이미지를 내려받는다. `<img src>`에 URL을 그대로 넣어 사용한다.

```
GET /files/{fileName}
```

**Headers**: 불필요 (비로그인 조회 허용)

> 공간 목록·상세가 비로그인 조회를 허용하므로 이미지도 함께 열어 둔다. 업로드(`POST /files`)만 인증이 필요하다.

**Path Variable**

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| fileName | string | **서버가 발급한 파일명만** 허용. `[0-9a-f]{32}.{확장자}` 패턴에 맞지 않으면 404 |

**Response** `200 OK`

```
Content-Type: image/jpeg   (확장자에 따라 image/png, image/webp, image/gif)

(이미지 바이너리)
```

**에러**

| 상태 | errorCode | 설명 |
| --- | --- | --- |
| 404 | `FILE_NOT_FOUND` | 패턴에 맞지 않는 파일명 / 경로 조작 시도 / 존재하지 않는 파일 |

---

### **3.3. 업로드 이미지 삭제**

업로드는 했지만 등록에 사용하지 않기로 한 이미지를 삭제한다. 화면에서 X 버튼으로 사진을 뺄 때 호출한다.

```
DELETE /files/{fileName}
```

**Headers**

```
Authorization: Bearer {accessToken}
```

**권한**: **업로드한 본인만** 삭제 가능. 파일명이 UUID여도 공개 URL로 노출되므로, 다른 사용자가 남의 이미지를 지우지 못하도록 업로더를 확인한다.

> 업로더 판별을 위해 업로드 시 `uploaded_files` 테이블에 (저장 파일명, 원본 파일명, 업로더 ID)를 함께 기록한다.

**Path Variable**

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| fileName | string | 삭제할 저장 파일명. `POST /files` 응답 `url`의 마지막 경로 세그먼트 |

**Response** `200 OK`

```json
{
  "success": true,
  "message": "이미지가 삭제되었습니다."
}
```

**에러**

| 상태 | errorCode | 설명 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 토큰 없음/만료 |
| 403 | `FILE_FORBIDDEN` | 다른 사용자가 업로드한 파일 |
| 404 | `FILE_NOT_FOUND` | 업로드 기록이 없는 파일명 |
| 500 | `FILE_STORAGE_FAILED` | 디스크 삭제 실패 |

> **디스크 파일이 이미 없는 경우**: 업로드 기록이 남아 있으면 정상 삭제(`200`)로 처리하고 기록만 정리한다.

> **남은 한계**: 등록을 완료하지 않고 폼을 이탈하면(뒤로가기·탭 닫기) 그때까지 업로드된 파일은 삭제 요청 없이 남는다. 공간에 연결되지 않은 파일의 일괄 정리는 아직 구현하지 않았다.

---


# 수익 예측

서버의 결정론적 수익 계산기(`ProfitCalculator`)로 월평균 수익성을 계산한다. Gemini를 호출하지 않으며, Python `Profit_Calculator` 원본의 계산 블록 1~10을 자바로 포팅한 것이라 같은 입력에 항상 같은 결과를 돌려준다.

### **4.1. 등록 전 수익 예측**

**아직 저장되지 않은 공간**의 면적과 희망 월세만으로 예상 수익을 계산한다.

```
POST /profit/estimate
```

**Headers**

```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**권한**: 로그인 필요. 역할 제한 없음.

> **`POST /ai/recommend`와의 차이**: AI 추천은 `spaceId`가 필수라 **이미 등록된 공간**만 계산할 수 있다. 이 API는 `spaceId`를 요구하지 않으므로 공간 등록 폼에 입력한 값만으로 **등록 전에** 예상 수익을 보여줄 수 있다.

**Request Body**

```json
{
  "area": 66,
  "monthlyRent": 500000
}
```

**필드 명세**

| 필드 | 타입 | 필수 | 제약 / 설명 |
| --- | --- | --- | --- |
| area | number | ✅ | > 0. 공실 전체면적(㎡) |
| monthlyRent | integer | ✅ | >= 0. 공간 제공자가 원하는 월세(원/월) |

**계산 대상 작물**

계산기 참조 데이터에 있는 작물 전체를 계산해 **공간 제공자 예상 배분수익(`landlordExpectedIncomeKrw`) 내림차순**으로 정렬해 반환한다. 즉 **배열 첫 항목이 이 공간에 가장 유리한 작물**이다.

| 작물 | kg당 판매단가 |
| --- | --- |
| 상추 | 8,000원 |
| 딸기 | 30,000원 |
| 바질 | 20,000원 |

**표준 가정값**

요청에 실측값이 없는 재배 파라미터는 아래 고정값을 사용하며, 응답에 그대로 실어 화면이 계산 근거를 노출할 수 있게 한다.

| 항목 | 값 |
| --- | --- |
| 재배 가능 바닥 비율 (`cultivableRatio`) | 0.6 |
| 다단 재배대 층 수 (`moduleLayers`) | 4층 |
| 천장고 (`ceilingHeightM`) | 2.5m |
| 공간 제공자 배분비율 (`landlordShareRatio`) | 0.8 |
| 감가상각 등 기타 월 비용 | 100,000원 |

**Response** `200 OK`

```json
{
  "success": true,
  "message": "수익 예측이 완료되었습니다.",
  "data": [
    {
      "cropName": "딸기",

      "totalAreaM2": 66.0,
      "cultivableRatio": 0.6,
      "areaUtilizationPercent": 60,
      "moduleLayers": 4,
      "ceilingHeightM": 2.5,
      "availableFloorAreaM2": 39.6,
      "cultivationAreaM2": 158.4,
      "lightingPowerW": 14143,
      "averageMonthlyEnergyKwh": 16606,

      "monthlyTotalProductionKg": 246,
      "monthlySalesKg": 221,
      "pricePerKgKrw": 30000,
      "monthlyRevenueKrw": 6629040,

      "electricityCostKrw": 2573894,
      "waterCostKrw": 14032,
      "materialCostKrw": 1191000,
      "laborCostKrw": 1266883,
      "depreciationAndOtherCostKrw": 100000,
      "monthlyOperatingCostKrw": 5145809,

      "monthlyOperatingProfitKrw": 1483231,
      "landlordShareRatio": 0.8,
      "landlordExpectedIncomeKrw": 1186584,
      "desiredMonthlyRentKrw": 500000,
      "businessOperatingProfitKrw": 296646,
      "operatingLoss": false,
      "longTermRecommended": true,
      "recommendation": "도심형 대량생산 스마트팜 방식 추천",
      "contractType": "장기계약형"
    }
  ]
}
```

> 위 예시는 `area=66`, `monthlyRent=500000` 요청의 실제 계산 결과 중 1위(딸기) 항목이다. 같은 요청에서 바질은 배분수익 86,824원(단기계약형), 상추는 **-1,640,740원**(적자·단기계약형)으로 이어진다.

**필드 명세 (data 배열 항목)**

모든 금액은 **KRW/월(반올림 정수)**, 면적은 ㎡, 전력량은 kWh/월 기준이다.

| 구분 | 필드 | 타입 | 설명 |
| --- | --- | --- | --- |
| 대상 | cropName | string | 계산 대상 작물명 |
| 계산 근거 | totalAreaM2 | number | 공간 전체 면적(㎡) |
| 계산 근거 | cultivableRatio | number | 재배 가능 바닥 비율 가정(0~1) |
| 계산 근거 | areaUtilizationPercent | integer | 면적 활용률(%) = 재배가능비율 × 100 |
| 계산 근거 | moduleLayers | integer | 다단 재배대 층 수 가정 |
| 계산 근거 | ceilingHeightM | number | 천장고 가정(m) |
| 계산 근거 | availableFloorAreaM2 | number | 재배 가능 바닥면적 = 전체면적 × 재배가능비율 |
| 계산 근거 | cultivationAreaM2 | number | 총 재배면적 = 바닥면적 × 다단 층수 |
| 계산 근거 | lightingPowerW | integer | 조명 정격 전력(W) |
| 계산 근거 | averageMonthlyEnergyKwh | integer | 월평균 환경제어 전력량(kWh) |
| 생산·매출 | monthlyTotalProductionKg | integer | 월 총생산량(kg) |
| 생산·매출 | monthlySalesKg | integer | 월 판매량(kg, 상품화율 반영) |
| 생산·매출 | pricePerKgKrw | integer | kg당 판매단가 |
| 생산·매출 | monthlyRevenueKrw | integer | 예상 월 매출 |
| 비용 | electricityCostKrw | integer | 예상 월 전기비 |
| 비용 | waterCostKrw | integer | 예상 월 수도비 |
| 비용 | materialCostKrw | integer | 예상 월 재료비 |
| 비용 | laborCostKrw | integer | 예상 월 인건비 |
| 비용 | depreciationAndOtherCostKrw | integer | 월 감가상각 등 기타비 |
| 비용 | monthlyOperatingCostKrw | integer | 예상 월 운영비 합계 |
| 손익 | monthlyOperatingProfitKrw | integer | 예상 월 영업이익. **음수면 손실** |
| 손익 | landlordShareRatio | number | 공간 제공자 배분비율(0~1) |
| 손익 | landlordExpectedIncomeKrw | integer | 공간 제공자 예상 배분수익 = 영업이익 × 배분비율 |
| 손익 | desiredMonthlyRentKrw | integer | 요청에 보낸 희망 월세. 배분수익과 비교 기준 |
| 손익 | businessOperatingProfitKrw | integer | 운영사 예상 영업이익 |
| 계약 추천 | operatingLoss | boolean | 영업 손실 여부 |
| 계약 추천 | longTermRecommended | boolean | 장기(스마트팜) 계약 추천 여부 |
| 계약 추천 | recommendation | string | 계약형태 추천 문구 |
| 계약 추천 | contractType | string | `장기계약형` / `단기계약형` |

**계약형태 추천 규칙**

| 조건 | contractType |
| --- | --- |
| 영업이익 < 0 | `단기계약형` (개인취미 대여 방식) |
| 영업이익 >= 0 **그리고** 배분수익 >= 희망 월세 | `장기계약형` (도심형 대량생산 스마트팜 방식) |
| 그 외 | `단기계약형` |

> **적자 표기**: 손실이 나도 `max(0, profit)` 처리를 하지 않고 **음수를 그대로 반환**한다. 계산 오류가 아니라 입력 조건에 따른 결과이므로 화면에서도 음수로 노출하는 것을 전제로 한다.

**에러**

| 상태 | errorCode | 설명 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `area` <= 0, `monthlyRent` < 0, 필수값 누락 |
| 401 | `UNAUTHORIZED` | 토큰 없음/만료 |

---

# 기존 API 변경사항

> 위 신규 API 도입에 따라 **2.1 / 2.3 / 2.5의 스키마가 변경**되었다. 해당 절 본문은 아직 갱신 전이므로 아래 내용을 함께 참고할 것.

### 공간 등록(2.1) / 공간 수정(2.5) — `floorPlanUrls` 추가

| 필드 | 타입 | 필수 | 제약 / 설명 |
| --- | --- | --- | --- |
| floorPlanUrls | string[] | ✅ (등록 시) | **도면 URL 배열. 최소 1장 ~ 최대 10장.** 재배 모듈 배치 검토에 필요해 필수 항목 |
| imageUrls | string[] | ❌ | 공간 사진. **최대 10장** 제한이 추가됨 |

- 도면은 공간 사진과 **별도 테이블(`space_floor_plans`)** 에 저장되며, 목록 카드의 대표 썸네일(`imageUrl`)에는 사용되지 않는다.
- 수정(2.5) 시 `floorPlanUrls`는 미포함이면 변경 없음, 배열을 보내면 전체 교체다. 다만 필수 항목이므로 **빈 배열로 전부 삭제할 수 없다**(1~10장).
- 등록 시 도면 누락 → `400 VALIDATION_ERROR` (`도면은 최소 1장 등록해야 합니다.`)

### 공간 상세 조회(2.3) — 응답에 `floorPlanUrls` 추가

```json
{
  "imageUrls": ["http://localhost:8080/api/files/{...}.jpg"],
  "floorPlanUrls": ["http://localhost:8080/api/files/{...}.png"]
}
```

이 변경 이전에 등록된 공간은 도면이 없으므로 `floorPlanUrls`가 빈 배열(`[]`)로 내려간다.

### AI 추천(`POST /ai/recommend`) — 응답에 `profitEstimate` 추가

응답 `data`에 서버가 계산한 예상 수익이 포함된다. 구조는 **4.1의 `data` 배열 항목과 동일**하다.

- 대표 작물(추천 순서상 계산기가 지원하는 첫 작물) 기준 1건
- 추천 작물 중 계산기 지원 작물(상추·딸기·바질)이 없으면 `null`
- 이 값이 이미 들어 있으므로 등록된 공간에 대해서는 `POST /profit/estimate`를 따로 호출할 필요가 없다
