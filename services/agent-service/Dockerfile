FROM python:3.12-slim

WORKDIR /app

# 安装依赖（单独层，利用 Docker 缓存）
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 拷贝应用源码
COPY app/ ./app/

EXPOSE 8083

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8083", "--workers", "2"]
