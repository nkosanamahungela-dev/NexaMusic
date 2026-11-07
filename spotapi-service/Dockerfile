FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1
WORKDIR /service

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# SpotAPI's async-v2 branch has a broken Hatch package path. Install its
# pinned source revision through the local packaging repair script.
RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*
COPY install_spotapi.sh .
RUN bash install_spotapi.sh

COPY app ./app
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
