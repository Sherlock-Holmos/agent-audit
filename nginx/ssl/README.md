# SSL 证书放置说明

请将生产证书放入此目录：

- `server.crt`
- `server.key`

本地测试可使用自签名证书：

```powershell
openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout server.key -out server.crt
```
