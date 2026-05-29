import os
from dotenv import load_dotenv

load_dotenv()

GROQ_API_KEY   = os.getenv("GROQ_API_KEY", "")
SPRING_API_URL = os.getenv("SPRING_API_URL", "http://localhost:8080/api")
SERVICE_USERNAME = os.getenv("SERVICE_USERNAME", "")
SERVICE_PASSWORD = os.getenv("SERVICE_PASSWORD", "")