from fastapi import FastAPI

from backend.src.api.routes import router as match_router
from backend.src.api.websocket import match_websocket, websocket_endpoint


app = FastAPI(title= "ScoreKeeperApp API")
app.include_router(match_router)
app.websocket("/ws/match")(match_websocket)
app.websocket("/ws/test")(websocket_endpoint)

@app.get("/")
def root():
    return {"message" : "ScoreKeeperApp backend is running"}