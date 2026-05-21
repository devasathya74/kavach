from rest_framework import serializers
from .models import (
    Officer, OfficerProfile, OfficerDevice, OfficerCredential,
    OfficerActivity, DraftChange, OverrideRequest,
    RankMaster, UnitMaster, CompanyMaster, PlatoonMaster,
    Incident, IncidentEvent, Broadcast, BroadcastAcknowledgment,
    FieldData, OtaUpdate, TrainingModule, TrainingAcknowledgment
)

# ... (Previous serializers) ...

class FieldDataSerializer(serializers.ModelSerializer):
    uploader_name = serializers.CharField(source='uploader.profile.name', read_only=True)
    
    class Meta:
        model = FieldData
        fields = [
            'id', 'uploader_name', 'title', 'file', 
            'category', 'tags', 'created_at', 'sha256', 
            'size_bytes', 'metadata'
        ]
        read_only_fields = ['uploader_name', 'sha256', 'size_bytes', 'created_at']

class OtaUpdateSerializer(serializers.ModelSerializer):
    class Meta:
        model = OtaUpdate
        fields = [
            'id', 'version_code', 'version_name', 'file', 
            'sha256', 'changelog', 'is_mandatory', 'status', 
            'created_at', 'released_at'
        ]
        read_only_fields = ['sha256', 'created_at']

class TrainingModuleSerializer(serializers.ModelSerializer):
    is_completed = serializers.SerializerMethodField()
    
    class Meta:
        model = TrainingModule
        fields = [
            'id', 'title', 'description', 'video_url', 
            'document', 'is_mandatory', 'is_completed', 'created_at'
        ]

    def get_is_completed(self, obj):
        request = self.context.get('request')
        if request and request.user.is_authenticated:
            return TrainingAcknowledgment.objects.filter(
                module=obj, officer=request.user, is_completed=True
            ).exists()
        return False

# ... (Previous master and core serializers) ...

# --- OPERATIONAL SERIALIZERS ---

class IncidentEventSerializer(serializers.ModelSerializer):
    actor_name = serializers.CharField(source='actor.profile.name', read_only=True)
    
    class Meta:
        model = IncidentEvent
        fields = ['id', 'event_type', 'actor_name', 'payload', 'timestamp', 'trace_id']

class IncidentSerializer(serializers.ModelSerializer):
    reported_by_name = serializers.CharField(source='reported_by.profile.name', read_only=True)
    timeline = IncidentEventSerializer(many=True, read_only=True)
    
    class Meta:
        model = Incident
        fields = [
            'id', 'incident_id', 'type', 'title', 'summary', 
            'severity', 'status', 'reported_by_name', 'unit',
            'latitude', 'longitude', 'location_name', 
            'occurred_at', 'reported_at', 'timeline', 'metadata'
        ]

class BroadcastSerializer(serializers.ModelSerializer):
    actor_name = serializers.CharField(source='actor.profile.name', read_only=True)
    ack_count = serializers.IntegerField(source='acknowledgments.count', read_only=True)
    
    class Meta:
        model = Broadcast
        fields = [
            'id', 'actor_name', 'title', 'content', 'image_url',
            'priority', 'targeted_officers', 'created_at', 'ack_count', 'metadata'
        ]

# --- MASTER DATA SERIALIZERS ---

class RankMasterSerializer(serializers.ModelSerializer):
    class Meta:
        model = RankMaster
        fields = ['id', 'code', 'name', 'level']

class UnitMasterSerializer(serializers.ModelSerializer):
    class Meta:
        model = UnitMaster
        fields = ['id', 'code', 'name']

class CompanyMasterSerializer(serializers.ModelSerializer):
    class Meta:
        model = CompanyMaster
        fields = ['id', 'code', 'name']

class PlatoonMasterSerializer(serializers.ModelSerializer):
    class Meta:
        model = PlatoonMaster
        fields = ['id', 'number', 'name']

# --- CORE SERIALIZERS ---

class OfficerProfileSerializer(serializers.ModelSerializer):
    rank = RankMasterSerializer(read_only=True)
    unit = UnitMasterSerializer(read_only=True)
    company = CompanyMasterSerializer(read_only=True)
    platoon = PlatoonMasterSerializer(read_only=True)
    
    # Writeable ID fields for convenience in some contexts (though we'll use DraftChange logic)
    rank_id = serializers.PrimaryKeyRelatedField(queryset=RankMaster.objects.all(), source='rank', write_only=True, required=False)
    unit_id = serializers.PrimaryKeyRelatedField(queryset=UnitMaster.objects.all(), source='unit', write_only=True, required=False)

    class Meta:
        model = OfficerProfile
        fields = [
            'name', 'rank', 'unit', 'company', 'platoon', 
            'phone', 'email', 'service_status', 'image',
            'rank_id', 'unit_id'
        ]

class OfficerDeviceSerializer(serializers.ModelSerializer):
    class Meta:
        model = OfficerDevice
        fields = [
            'id', 'device_id', 'device_name', 'manufacturer', 
            'android_version', 'app_version', 'status', 
            'integrity_level', 'trust_score', 'last_active'
        ]

import re

def validate_secure_password(password: str):
    if len(password) < 8:
        raise serializers.ValidationError("Password minimum 8 characters hona chahiye.")
    if not re.search(r"[A-Z]", password):
        raise serializers.ValidationError("Minimum 1 capital letter required.")
    if not re.search(r"[a-z]", password):
        raise serializers.ValidationError("Minimum 1 small letter required.")
    if len(re.findall(r"\d", password)) < 2:
        raise serializers.ValidationError("Minimum 2 numbers required.")
    if not re.search(r"[@$!%*?&#]", password):
        raise serializers.ValidationError("Minimum 1 special character required.")
    return password

class OfficerSerializer(serializers.ModelSerializer):
    profile = OfficerProfileSerializer(read_only=True)
    devices = OfficerDeviceSerializer(many=True, read_only=True)
    unit = UnitMasterSerializer(read_only=True)
    discipline_score = serializers.ReadOnlyField(source='discipline_score_val')
    operational_level = serializers.ReadOnlyField(source='operational_level_val')
    
    password = serializers.CharField(write_only=True, required=False)

    def validate_password(self, value):
        if value:
            return validate_secure_password(value)
        return value

    class Meta:
        model = Officer
        fields = [
            'id', 'pno', 'role', 'unit', 'is_active', 'profile', 
            'devices', 'discipline_score', 'operational_level', 'created_at',
            'password', 'must_change_password'
        ]

# --- GOVERNANCE SERIALIZERS ---

class DraftChangeSerializer(serializers.ModelSerializer):
    target_name = serializers.CharField(source='target.profile.name', read_only=True)
    actor_name = serializers.CharField(source='actor.profile.name', read_only=True)
    target_pno = serializers.CharField(source='target.pno', read_only=True)

    class Meta:
        model = DraftChange
        fields = [
            'id', 'actor', 'actor_name', 'target', 'target_name', 'target_pno',
            'model', 'field', 'old_value', 'new_value', 'status', 'created_at'
        ]

class OfficerActivitySerializer(serializers.ModelSerializer):
    class Meta:
        model = OfficerActivity
        fields = [
            'id', 'pno', 'action', 'severity', 'result', 
            'route', 'ip_address', 'created_at', 'metadata'
        ]
        read_only_fields = fields # Immutability

class OverrideRequestSerializer(serializers.ModelSerializer):
    pilot_pno = serializers.CharField(source='pilot.pno', read_only=True)
    target_pno = serializers.CharField(source='target_user.pno', read_only=True)
    
    class Meta:
        model = OverrideRequest
        fields = [
            'id', 'pilot_pno', 'target_pno', 'field', 
            'previous_value', 'proposed_value', 'reason', 
            'status', 'created_at', 'resolved_at'
        ]

class DraftChangeSerializer(serializers.ModelSerializer):
    class Meta:
        model = DraftChange
        fields = [
            'id', 'model', 'field', 'old_value', 
            'new_value', 'status', 'created_at', 'applied_at'
        ]
