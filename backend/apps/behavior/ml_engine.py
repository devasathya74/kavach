import joblib
import os
import numpy as np
from django.conf import settings

MODEL_PATH = os.path.join(settings.BASE_DIR, 'models', 'anomaly_model.pkl')

class AnomalyMLDetector:
    def __init__(self):
        self.model = self._load_model()

    def _load_model(self):
        if os.path.exists(MODEL_PATH):
            try:
                return joblib.load(MODEL_PATH)
            except:
                return None
        return None

    def predict_anomaly_score(self, features):
        """
        Predicts how anomalous a behavior vector is.
        Returns score (negative is anomalous, positive is normal)
        """
        if self.model is None:
            return 0.5 # Neutral fallback

        # Reshape for single sample
        X = np.array(features).reshape(1, -1)
        
        # Isolation Forest decision_function returns signed proximity
        # Normal samples have positive scores, abnormal have negative
        score = self.model.decision_function(X)[0]
        return score

    def train_initial_model(self, data_matrix):
        """
        Initial training on clean historical data.
        """
        from sklearn.ensemble import IsolationForest
        model = IsolationForest(contamination=0.1, random_state=42)
        model.fit(data_matrix)
        
        os.makedirs(os.path.dirname(MODEL_PATH), exist_ok=True)
        joblib.dump(model, MODEL_PATH)
        self.model = model
        return True
