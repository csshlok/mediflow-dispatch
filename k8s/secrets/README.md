# Kubernetes Secrets

`db-secrets.yaml` and `jwt-secret.yaml` are local-only manifests and are ignored by Git.

Create or update them with the keys expected by the service `application.yaml` files:

- `USER_DB_USERNAME`, `USER_DB_PASSWORD`
- `EMERGENCY_DB_USERNAME`, `EMERGENCY_DB_PASSWORD`
- `AMBULANCE_DB_USERNAME`, `AMBULANCE_DB_PASSWORD`
- `HOSPITAL_DB_USERNAME`, `HOSPITAL_DB_PASSWORD`
- `MATCHING_USERNAME`, `MATCHING_PASSWORD`
- `CASE_DB_USERNAME`, `CASE_DB_PASSWORD`
- `NOTIFICATION_DB_USERNAME`, `NOTIFICATION_DB_PASSWORD`
- `JWT_SECRET`

Apply them locally with:

```powershell
kubectl apply -f k8s\secrets\db-secrets.yaml
kubectl apply -f k8s\secrets\jwt-secret.yaml
```
