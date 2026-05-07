# infra/aws — AWS 자격 증명 + EKS 부하 발생기 사전 인프라

ADR-0013 박제 전 **사전 환경**. 30만 RPS 실제 재현용 k6-operator on EKS Seoul (mixed Graviton spot) 클러스터 운용에 필요한 자격 증명 템플릿과 IAM 가이드를 박제한다.

머지/적용은 ADR-0013 본 박제 + INFRA-2 task에서 진행. 본 회차는 템플릿만.

---

## 1. 사용 절차

```bash
# 1) 템플릿 복사
cp infra/aws/.env.aws.example infra/aws/.env.aws

# 2) .env.aws 편집해서 실제 키 입력
#    또는 (권장) ~/.aws/credentials profile 사용 + AWS_PROFILE 만 export
${EDITOR:-vi} infra/aws/.env.aws

# 3) (권장) 로컬 셸에서 사용
set -a; . infra/aws/.env.aws; set +a
aws sts get-caller-identity
```

`.env.aws` 자체는 `.gitignore` (root) 에 박제됨 — 커밋 차단.

---

## 2. 권장 보관 위치

`~/.aws/credentials` 가 1순위. 워크스페이스 외부에 두고, profile 이름만 변수로 주입:

```ini
# ~/.aws/credentials
[tourdoum-loadtest]
aws_access_key_id     = AKIA...
aws_secret_access_key = ...
region                = ap-northeast-2
```

```bash
export AWS_PROFILE=tourdoum-loadtest
aws sts get-caller-identity
```

장기 키 발급보다 STS AssumeRole / IAM Identity Center / IRSA(EKS Pod role) 가 안전. 학습 단계는 long-lived 허용.

---

## 3. IAM 최소 권한 가이드 (k6-operator on EKS)

본 사용자/역할이 가진 권한은 **부하 발생기 클러스터 라이프사이클** 한정. 본 워크스페이스의 다른 AWS 리소스(특히 운영 RDS/S3)에는 접근하지 못하게 한다.

### Managed Policy 후보
- `AmazonEKSClusterPolicy` (cluster 자체)
- `AmazonEKSWorkerNodePolicy` (node group)
- `AmazonEC2ContainerRegistryReadOnly` (k6 image pull)

### Inline 추가 (cluster 생성/삭제 + spot fleet 운영)
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "EksLifecycle",
      "Effect": "Allow",
      "Action": [
        "eks:CreateCluster",
        "eks:DeleteCluster",
        "eks:DescribeCluster",
        "eks:ListClusters",
        "eks:UpdateClusterConfig",
        "eks:CreateNodegroup",
        "eks:DeleteNodegroup",
        "eks:DescribeNodegroup",
        "eks:UpdateNodegroupConfig"
      ],
      "Resource": "*"
    },
    {
      "Sid": "Ec2Loadtest",
      "Effect": "Allow",
      "Action": [
        "ec2:Describe*",
        "ec2:RunInstances",
        "ec2:TerminateInstances",
        "ec2:CreateTags",
        "ec2:CreateLaunchTemplate",
        "ec2:DeleteLaunchTemplate"
      ],
      "Resource": "*"
    },
    {
      "Sid": "IamPassRoleForEks",
      "Effect": "Allow",
      "Action": ["iam:PassRole", "iam:GetRole", "iam:CreateServiceLinkedRole"],
      "Resource": "*",
      "Condition": {
        "StringEquals": { "iam:PassedToService": ["eks.amazonaws.com", "ec2.amazonaws.com"] }
      }
    },
    {
      "Sid": "CloudWatchLogs",
      "Effect": "Allow",
      "Action": ["logs:CreateLogGroup", "logs:DescribeLogGroups", "logs:PutRetentionPolicy"],
      "Resource": "*"
    }
  ]
}
```

운영 환경에선 `Resource`를 ARN 패턴(예: `arn:aws:eks:ap-northeast-2:<acct>:cluster/tourdoum-loadtest-eks`)으로 좁힐 것.

---

## 4. 비밀값 절대 박제 금지

- `.env.aws` (실제 키) 커밋 금지 — gitleaks 차단.
- 본 README/ADR 어디에도 placeholder 외 키 박제 금지.
- 분실 시 IAM 콘솔에서 access key 회전 + 즉시 비활성화.

---

## 5. 다음 단계 (INFRA-2 / ADR-0013 후속)

- [ ] eksctl / Terraform로 클러스터 부트스트랩 스크립트 박제
- [ ] kube-prometheus-stack helm release 박제 (Prometheus + Grafana + Alertmanager)
- [ ] loki-stack helm release 박제 (Loki + Promtail)
- [ ] k6-operator install + sample TestRun manifest
- [ ] Mixed Graviton spot node group + scaling 정책
