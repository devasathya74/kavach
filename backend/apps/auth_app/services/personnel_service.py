from django.db import transaction
from django.utils import timezone
from ..models import Officer, OfficerProfile, OfficerCredential, OfficerActivity, UnitMaster, RankMaster, CompanyMaster
from ..security.roles import can_manage, requires_approval
from ..models.governance import DraftChange

class PersonnelService:
    
    @staticmethod
    def create_user(actor, data: dict):
        print(f"DEBUG: PersonnelService.create_user | ACTOR: {actor.pno} | DATA: {data}")
        role = data.get('role', 'USER')
        
        # We create a dummy target to check if actor CAN create this role
        # but simpler: check if actor role in [ADMIN, PILOT] and target role is lower or equal
        if not can_manage(actor, Officer(role=role)):
            raise PermissionError(f"Role {actor.role} cannot create {role}")

        with transaction.atomic():
            # 1. Resolve Unit (Handle ID or Code)
            unit_val = data.get('unit_id') or data.get('unit')
            if isinstance(unit_val, int) or (isinstance(unit_val, str) and unit_val.isdigit()):
                unit = UnitMaster.objects.get(id=unit_val)
            else:
                unit = UnitMaster.objects.get(code=unit_val)
            
            # 2. Hierarchy Validation (Company)
            company_val = data.get('company_id') or data.get('company')
            company = None
            if company_val:
                if isinstance(company_val, int) or (isinstance(company_val, str) and company_val.isdigit()):
                    company = CompanyMaster.objects.get(id=company_val)
                else:
                    company = CompanyMaster.objects.get(unit=unit, code=company_val)

            # 3. Resolve Rank
            rank_val = data.get('rank_id') or data.get('rank')
            if isinstance(rank_val, int) or (isinstance(rank_val, str) and rank_val.isdigit()):
                rank_id = rank_val
            else:
                rank = RankMaster.objects.get(code=rank_val)
                rank_id = rank.id

            # 4. Create Officer
            officer = Officer.objects.create_user(
                pno=data['pno'],
                role=role,
                unit=unit,
                password=data['password']
            )
            
            # 5. Setup Profile
            OfficerProfile.objects.create(
                officer=officer,
                name=data['name'],
                rank_id=rank_id,
                unit=unit,
                company=company,
                platoon_id=data.get('platoon_id'),
                phone=data.get('phone', ''),
                email=data.get('email')
            )
            
            # 5. Setup Credentials
            OfficerCredential.objects.create(
                officer=officer,
                password_hash=officer.password # Django already hashed it in create_user
            )

            # 6. Audit
            PersonnelService._audit(actor, 'USER_CREATE', officer, severity='SECURITY')
            
            return officer

    @staticmethod
    def update_user(actor, officer_id: str, data: dict):
        print(f"DEBUG: PersonnelService.update_user | ACTOR: {actor.pno} | TARGET: {officer_id} | DATA: {data}")
        officer = Officer.objects.get(id=officer_id)
        if not can_manage(actor, officer):
            raise PermissionError("Access Denied: Insufficient Rank/Role Authority")

        # Flexible Field Extraction
        rank_id = data.get('rank_id') or data.get('rank')
        role = data.get('role') or data.get('system_role')
        
        sensitive_fields = ['role', 'rank_id', 'rank_code', 'pno', 'service_status', 'is_active']
        changes = {}
        requires_ratification = False

        # Only enforce ratification for lower roles (non-PILOT/ADMIN)
        if actor.role not in ["PILOT", "ADMIN"]:
            for field in sensitive_fields:
                val = data.get(field)
                if field == 'rank_id' and rank_id: val = rank_id
                if field == 'role' and role: val = role
                
                if val is not None:
                    requires_ratification = True
                    changes[field] = val

        if requires_ratification or requires_approval('UPDATE', actor.role, officer.role):
            print(f"DEBUG: Update requires ratification for {officer_id}")
            return PersonnelService._propose_change(actor, officer, data, 'UPDATE')

        # Direct Update logic
        with transaction.atomic():
            profile = officer.profile
            
            if 'name' in data: profile.name = data['name']
            if 'phone' in data: profile.phone = data['phone']
            if 'email' in data: profile.email = data['email']
            
            if rank_id:
                if isinstance(rank_id, int) or (isinstance(rank_id, str) and rank_id.isdigit()):
                    profile.rank_id = rank_id
                else:
                    profile.rank = RankMaster.objects.get(code=rank_id)
            
            # Unit/Hierarchy updates (Flexible keys)
            unit_val = data.get('unit_id', data.get('unit', '_NOT_PROVIDED_'))
            if unit_val != '_NOT_PROVIDED_':
                if unit_val is None:
                    profile.unit = None
                elif isinstance(unit_val, int) or (isinstance(unit_val, str) and unit_val.isdigit()):
                    profile.unit_id = unit_val
                else:
                    profile.unit = UnitMaster.objects.get(code=unit_val)
            
            company_val = data.get('company_id', data.get('company', '_NOT_PROVIDED_'))
            if company_val != '_NOT_PROVIDED_':
                if company_val is None:
                    profile.company = None
                elif isinstance(company_val, int) or (isinstance(company_val, str) and company_val.isdigit()):
                    profile.company_id = company_val
                else:
                    target_unit = profile.unit
                    profile.company = CompanyMaster.objects.get(unit=target_unit, code=company_val)
            
            platoon_val = data.get('platoon_id', data.get('platoon', '_NOT_PROVIDED_'))
            if platoon_val != '_NOT_PROVIDED_':
                if platoon_val is None:
                    profile.platoon = None
                elif isinstance(platoon_val, int) or (isinstance(platoon_val, str) and platoon_val.isdigit()):
                    profile.platoon_id = platoon_val
                else:
                    profile.platoon = PlatoonMaster.objects.get(company=profile.company, number=platoon_val)
            
            profile.save()
            
            if role:
                officer.role = role
            if 'is_active' in data:
                officer.is_active = data['is_active']
            if 'pno' in data:
                officer.pno = data['pno']
            
            officer.save()
            
            PersonnelService._audit(actor, 'USER_UPDATE', officer)
            return officer

    @staticmethod
    def reset_password(actor, officer_id: str, new_password: str):
        officer = Officer.objects.get(id=officer_id)
        if not can_manage(actor.role, officer.role):
            raise PermissionError("Access Denied")

        with transaction.atomic():
            officer.set_password(new_password)
            officer.must_change_password = True
            officer.save()
            
            # Record in history
            cred = officer.credentials
            cred.last_password_hash = cred.password_hash
            cred.password_hash = officer.password
            cred.password_changed_at = timezone.now()
            cred.save()
            
            PersonnelService._audit(actor, 'PASSWORD_RESET', officer, severity='SECURITY')

    @staticmethod
    def toggle_status(actor, officer_id: str):
        officer = Officer.objects.get(id=officer_id)
        if not can_manage(actor.role, officer.role):
            raise PermissionError("Access Denied")
            
        if requires_approval('SUSPEND', actor.role, officer.role):
            return PersonnelService._propose_change(actor, officer, {'is_active': not officer.is_active}, 'SUSPEND')

        officer.is_active = not officer.is_active
        officer.save()
        
        action = 'USER_ACTIVATE' if officer.is_active else 'USER_SUSPEND'
        PersonnelService._audit(actor, action, officer, severity='WARNING')
        return officer

    @staticmethod
    def _propose_change(actor, target, data, action_type):
        """Creates a DraftChange for CO approval"""
        return DraftChange.objects.create(
            actor=actor,
            target=target,
            unit=actor.unit,
            model='Officer',
            field=action_type,
            old_value={'current': 'active' if target.is_active else 'suspended'},
            new_value=data,
            status='PENDING'
        )

    @staticmethod
    def _audit(actor, action, target, severity='INFO'):
        OfficerActivity.objects.create(
            actor=actor,
            officer=target,
            pno=target.pno,
            unit=actor.unit,
            action=action,
            severity=severity,
            result='SUCCESS'
        )
