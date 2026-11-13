/**
 * 开放 API 文档与多语言 SDK 示例生成
 */
import { seoSiteUrl } from './seo.js';

export function resolveOpenApiBase() {
  const envBase = String(import.meta.env.VITE_API_BASE || '').replace(/\/$/, '');
  if (envBase) return `${envBase}/api/open/ai`;
  if (typeof window !== 'undefined' && window.location?.origin) {
    return `${window.location.origin}/api/open/ai`;
  }
  return `${seoSiteUrl}/api/open/ai`;
}

export const OPEN_API_MODELS = [
  {
    id: 'virtual_tryon',
    zh: '单件虚拟换装',
    en: 'Virtual try-on (single)',
    payload: '{"modelUrl":"https://example.com/model.jpg","garmentUrl":"https://example.com/garment.jpg","category":"auto","outputResolution":"1K"}',
  },
  {
    id: 'batch_tryon',
    zh: '批量虚拟换装',
    en: 'Batch try-on',
    payload: '{"modelUrl":"https://example.com/model.jpg","garmentUrls":["https://example.com/g1.jpg","https://example.com/g2.jpg"],"category":"auto","outputResolution":"1K"}',
  },
  {
    id: 'garment_extract',
    zh: '服装精修抠图',
    en: 'Garment extract / retouch',
    payload: '{"imageUrls":["https://example.com/raw.jpg"],"backgroundColor":"#FFFFFF","garmentScope":"FULL","aspectRatio":"1:1","imageSize":"1K"}',
    multipart: true,
  },
  {
    id: 'portrait_studio',
    zh: 'AI 写真',
    en: 'Portrait studio',
    payload: '{"stylePreset":"BUSINESS","makeupPreset":"ORIGINAL","outfitMode":"STYLE","outputCount":4,"aspectRatio":"3:4","imageSize":"1K"}',
    multipart: true,
  },
  {
    id: 'model_shot',
    zh: '模特出图',
    en: 'Model shot / packshot',
    payload: '{"artDirection":"clean studio","modelGender":"female","sceneVariantCount":2,"aspectRatio":"3:4","imageSize":"1K"}',
    multipart: true,
  },
  {
    id: 'garment_suite',
    zh: '服装电商套图',
    en: 'Garment e-commerce suite',
    payload: '{"aspectRatio":"1:1","imageSize":"1K","outputLanguage":"zh"}',
    multipart: true,
  },
];

export function buildSdkExamples(baseUrl, apiKey, model = 'virtual_tryon') {
  const key = apiKey || 'fsk_your_api_key_here';
  const m = OPEN_API_MODELS.find((x) => x.id === model) || OPEN_API_MODELS[0];
  const payload = m.payload;
  const submitUrl = `${baseUrl}/submit`;
  const taskUrl = `${baseUrl}/task?model=${model}&taskId=123456`;

  return {
    curl: `# 提交任务
curl -X POST "${submitUrl}" \\
  -H "X-Api-Key: ${key}" \\
  -F "model=${model}" \\
  -F 'payload=${payload}'

# 查询任务
curl "${taskUrl}" \\
  -H "X-Api-Key: ${key}"`,

    python: `import requests

API_KEY = "${key}"
BASE = "${baseUrl}"

# 提交任务
resp = requests.post(
    f"{BASE}/submit",
    headers={"X-Api-Key": API_KEY},
    data={"model": "${model}", "payload": """${payload}"""},
    timeout=120,
)
resp.raise_for_status()
data = resp.json()["data"]
task_id = data.get("taskId") or (data.get("taskIds") or [None])[0]
print("task_id:", task_id)

# 查询任务
if task_id:
    q = requests.get(
        f"{BASE}/task",
        headers={"X-Api-Key": API_KEY},
        params={"model": "${model}", "taskId": task_id},
        timeout=30,
    )
    q.raise_for_status()
    print(q.json())`,

    node: `const API_KEY = "${key}";
const BASE = "${baseUrl}";

async function submit() {
  const form = new FormData();
  form.append("model", "${model}");
  form.append("payload", \`${payload}\`);

  const res = await fetch(\`\${BASE}/submit\`, {
    method: "POST",
    headers: { "X-Api-Key": API_KEY },
    body: form,
  });
  const json = await res.json();
  if (json.code !== 200 && json.code !== 0) throw new Error(json.message);
  return json.data;
}

async function queryTask(taskId) {
  const url = new URL(\`\${BASE}/task\`);
  url.searchParams.set("model", "${model}");
  url.searchParams.set("taskId", String(taskId));
  const res = await fetch(url, { headers: { "X-Api-Key": API_KEY } });
  const json = await res.json();
  if (json.code !== 200 && json.code !== 0) throw new Error(json.message);
  return json.data;
}

submit().then((d) => console.log(d));`,

    java: `import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class FashTryOpenApi {
    private static final String API_KEY = "${key}";
    private static final String BASE = "${baseUrl}";

    public static void main(String[] args) throws Exception {
        String boundary = UUID.randomUUID().toString();
        String body = "--" + boundary + "\\r\\n"
            + "Content-Disposition: form-data; name=\\"model\\"\\r\\n\\r\\n${model}\\r\\n"
            + "--" + boundary + "\\r\\n"
            + "Content-Disposition: form-data; name=\\"payload\\"\\r\\n\\r\\n${payload.replace(/"/g, '\\"')}\\r\\n"
            + "--" + boundary + "--\\r\\n";

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BASE + "/submit"))
            .header("X-Api-Key", API_KEY)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println(resp.body());
    }
}`,

    go: `package main

import (
    "bytes"
    "fmt"
    "io"
    "mime/multipart"
    "net/http"
)

const apiKey = "${key}"
const baseURL = "${baseUrl}"

func main() {
    var buf bytes.Buffer
    w := multipart.NewWriter(&buf)
    _ = w.WriteField("model", "${model}")
    _ = w.WriteField("payload", \`${payload}\`)
    w.Close()

    req, _ := http.NewRequest(http.MethodPost, baseURL+"/submit", &buf)
    req.Header.Set("Content-Type", w.FormDataContentType())
    req.Header.Set("X-Api-Key", apiKey)

    resp, err := http.DefaultClient.Do(req)
    if err != nil {
        panic(err)
    }
    defer resp.Body.Close()
    body, _ := io.ReadAll(resp.Body)
    fmt.Println(string(body))
}`,

    php: `<?php
$apiKey = "${key}";
$base = "${baseUrl}";

$ch = curl_init("$base/submit");
curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_HTTPHEADER => ["X-Api-Key: $apiKey"],
    CURLOPT_POSTFIELDS => [
        "model" => "${model}",
        "payload" => '${payload}',
    ],
    CURLOPT_RETURNTRANSFER => true,
]);
$resp = curl_exec($ch);
curl_close($ch);
echo $resp;

// 查询：GET $base/task?model=${model}&taskId=123456`,
  };
}

export const SDK_LANGS = [
  { id: 'curl', label: 'cURL' },
  { id: 'python', label: 'Python' },
  { id: 'node', label: 'Node.js' },
  { id: 'java', label: 'Java' },
  { id: 'go', label: 'Go' },
  { id: 'php', label: 'PHP' },
];
