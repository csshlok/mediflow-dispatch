# Pod Recovery Time Evidence

Collected on 2026-06-16 against `notification-service`.

- Deleted pod: `notification-service-77654fdc8-2q94p`
- Replacement pod: `notification-service-77654fdc8-6hhv8`
- Time from deletion command to replacement pod reaching `Running` and ready: 135.45 seconds

Command shape used:

```powershell
kubectl delete pod <notification-pod> -n mediflow --wait=false
```

The deployment returned to `1/1` available after Kubernetes created the replacement pod.
