import argparse
import multiprocessing
import uvicorn
from backend.src.api.main import app


def main():
    parser = argparse.ArgumentParser(description="ScoreKeeper Backend")
    parser.add_argument("--port", type=int, default=8000, help="Порт для запуска сервера")
    parser.add_argument("--host", type=str, default="127.0.0.1")
    args = parser.parse_args()
    
    log_config = {
        "version": 1,
        "disable_existing_loggers": True,
        "handlers": {
            "file_handler": {
                "class": "logging.FileHandler",
                "filename": "server.log",
            },
        },
        "root": {
            "handlers": ["file_handler"],
            "level": "INFO",
        },
    }
    
    uvicorn.run(
        app,
        host=args.host,
        port=args.port,
        reload=False,
        log_config=log_config
    )
    

if __name__ == "__main__":
    multiprocessing.freeze_support()
    main()