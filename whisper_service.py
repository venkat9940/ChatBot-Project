from flask import Flask, request, jsonify
import whisper
import os

app = Flask(__name__)
model = whisper.load_model("base")

@app.route('/transcribe', methods=['POST'])
def transcribe():
    audio = request.files['audio']
    audio.save("temp.wav")
    result = model.transcribe("temp.wav")
    os.remove("temp.wav")
    return jsonify({"text": result["text"]})

if __name__ == '__main__':
    app.run(port=5005)
