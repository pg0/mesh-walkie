FROM python:3.12-alpine

ENV PYTHONUNBUFFERED=1

RUN adduser -D -H relay
WORKDIR /app
COPY relay.py .
USER relay

EXPOSE 51820
CMD ["python", "relay.py"]
