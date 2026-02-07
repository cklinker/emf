import React, { useState, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { useApi } from '../../context/ApiContext';
import { useI18n } from '../../context/I18nContext';
import { useToast } from '../../components/Toast';
import styles from './UserDetailPage.module.css';

interface PlatformUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  username?: string;
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED' | 'PENDING_ACTIVATION';
  locale: string;
  timezone: string;
  profileId?: string;
  managerId?: string;
  lastLoginAt?: string;
  loginCount: number;
  mfaEnabled: boolean;
  createdAt: string;
  updatedAt: string;
}

interface LoginHistoryEntry {
  id: string;
  userId: string;
  loginTime: string;
  sourceIp: string;
  loginType: 'UI' | 'API' | 'OAUTH' | 'SERVICE_ACCOUNT';
  status: 'SUCCESS' | 'FAILED' | 'LOCKED_OUT';
  userAgent: string;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

interface UpdateFormData {
  firstName: string;
  lastName: string;
  username: string;
  locale: string;
  timezone: string;
}

export interface UserDetailPageProps {
  testId?: string;
}

function StatusBadge({ status }: { status: string }) {
  const colorMap: Record<string, string> = {
    ACTIVE: styles.statusActive,
    INACTIVE: styles.statusInactive,
    LOCKED: styles.statusLocked,
    PENDING_ACTIVATION: styles.statusPending,
  };

  return (
    <span className={`${styles.statusBadge} ${colorMap[status] || ''}`}>
      {status}
    </span>
  );
}

function LoginStatusLabel({ status }: { status: string }) {
  const colorMap: Record<string, string> = {
    SUCCESS: styles.loginStatusSuccess,
    FAILED: styles.loginStatusFailed,
    LOCKED_OUT: styles.loginStatusLocked,
  };

  return <span className={colorMap[status] || ''}>{status}</span>;
}

export function UserDetailPage({ testId = 'user-detail-page' }: UserDetailPageProps) {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const { t, formatDate } = useI18n();
  const { apiClient } = useApi();
  const { showToast } = useToast();
  const navigate = useNavigate();

  const [activeTab, setActiveTab] = useState<'details' | 'loginHistory'>('details');
  const [isEditing, setIsEditing] = useState(false);
  const [formData, setFormData] = useState<UpdateFormData>({
    firstName: '',
    lastName: '',
    username: '',
    locale: '',
    timezone: '',
  });
  const [historyPage, setHistoryPage] = useState(0);

  const { data: user, isLoading, error, refetch } = useQuery({
    queryKey: ['users', id],
    queryFn: async () => {
      const result = await apiClient.get<PlatformUser>(`/control/users/${id}`);
      setFormData({
        firstName: result.firstName,
        lastName: result.lastName,
        username: result.username || '',
        locale: result.locale,
        timezone: result.timezone,
      });
      return result;
    },
    enabled: !!id,
  });

  const { data: loginHistory, isLoading: historyLoading } = useQuery({
    queryKey: ['users', id, 'login-history', historyPage],
    queryFn: async () => {
      const params = new URLSearchParams();
      params.append('page', historyPage.toString());
      params.append('size', '20');
      return apiClient.get<PageResponse<LoginHistoryEntry>>(
        `/control/users/${id}/login-history?${params}`
      );
    },
    enabled: !!id && activeTab === 'loginHistory',
  });

  const updateMutation = useMutation({
    mutationFn: (data: UpdateFormData) =>
      apiClient.put<PlatformUser>(`/control/users/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users', id] });
      queryClient.invalidateQueries({ queryKey: ['users'] });
      showToast(t('users.updateSuccess'), 'success');
      setIsEditing(false);
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error');
    },
  });

  const statusMutation = useMutation({
    mutationFn: (action: 'deactivate' | 'activate') =>
      apiClient.post(`/control/users/${id}/${action}`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users', id] });
      queryClient.invalidateQueries({ queryKey: ['users'] });
      showToast(t('users.statusUpdateSuccess'), 'success');
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error');
    },
  });

  const handleSave = useCallback(() => {
    updateMutation.mutate(formData);
  }, [formData, updateMutation]);

  const handleCancel = useCallback(() => {
    if (user) {
      setFormData({
        firstName: user.firstName,
        lastName: user.lastName,
        username: user.username || '',
        locale: user.locale,
        timezone: user.timezone,
      });
    }
    setIsEditing(false);
  }, [user]);

  if (error) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.errorState}>
          <p>{t('errors.generic')}</p>
          <button onClick={() => refetch()} className={styles.btnPrimary}>
            {t('common.retry')}
          </button>
        </div>
      </div>
    );
  }

  if (isLoading || !user) {
    return (
      <div className={styles.container} data-testid={testId}>
        <div className={styles.loadingState}>{t('common.loading')}</div>
      </div>
    );
  }

  const historyEntries = loginHistory?.content ?? [];
  const historyTotalPages = loginHistory?.totalPages ?? 0;

  return (
    <div className={styles.container} data-testid={testId}>
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <button className={styles.backButton} onClick={() => navigate('/users')}>
            {t('common.back')}
          </button>
          <h1>{user.firstName} {user.lastName}</h1>
          <StatusBadge status={user.status} />
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          {user.status === 'ACTIVE' ? (
            <button
              className={styles.btnDanger}
              onClick={() => statusMutation.mutate('deactivate')}
              disabled={statusMutation.isPending}
            >
              {t('users.deactivate')}
            </button>
          ) : (
            <button
              className={styles.btnPrimary}
              onClick={() => statusMutation.mutate('activate')}
              disabled={statusMutation.isPending}
            >
              {t('users.activate')}
            </button>
          )}
        </div>
      </header>

      <div className={styles.tabs}>
        <button
          className={`${styles.tab} ${activeTab === 'details' ? styles.tabActive : ''}`}
          onClick={() => setActiveTab('details')}
        >
          {t('users.details')}
        </button>
        <button
          className={`${styles.tab} ${activeTab === 'loginHistory' ? styles.tabActive : ''}`}
          onClick={() => setActiveTab('loginHistory')}
        >
          {t('users.loginHistory')}
        </button>
      </div>

      {activeTab === 'details' && (
        <div className={styles.card}>
          <div className={styles.formGrid}>
            <div className={styles.formGroup}>
              <label>{t('users.email')}</label>
              <input type="email" value={user.email} disabled />
            </div>
            <div className={styles.formGroup}>
              <label>{t('users.username')}</label>
              <input
                type="text"
                value={formData.username}
                onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                disabled={!isEditing}
              />
            </div>
            <div className={styles.formGroup}>
              <label>{t('users.firstName')}</label>
              <input
                type="text"
                value={formData.firstName}
                onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                disabled={!isEditing}
              />
            </div>
            <div className={styles.formGroup}>
              <label>{t('users.lastName')}</label>
              <input
                type="text"
                value={formData.lastName}
                onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                disabled={!isEditing}
              />
            </div>
            <div className={styles.formGroup}>
              <label>{t('users.locale')}</label>
              <input
                type="text"
                value={formData.locale}
                onChange={(e) => setFormData({ ...formData, locale: e.target.value })}
                disabled={!isEditing}
              />
            </div>
            <div className={styles.formGroup}>
              <label>{t('users.timezone')}</label>
              <input
                type="text"
                value={formData.timezone}
                onChange={(e) => setFormData({ ...formData, timezone: e.target.value })}
                disabled={!isEditing}
              />
            </div>
          </div>

          <div className={styles.infoRow}>
            <div className={styles.infoItem}>
              <span className={styles.infoLabel}>{t('users.lastLogin')}</span>
              <span>{user.lastLoginAt ? formatDate(user.lastLoginAt) : t('users.never')}</span>
            </div>
            <div className={styles.infoItem}>
              <span className={styles.infoLabel}>{t('users.loginCount')}</span>
              <span>{user.loginCount}</span>
            </div>
            <div className={styles.infoItem}>
              <span className={styles.infoLabel}>{t('users.mfaEnabled')}</span>
              <span>{user.mfaEnabled ? t('common.yes') : t('common.no')}</span>
            </div>
            <div className={styles.infoItem}>
              <span className={styles.infoLabel}>{t('users.created')}</span>
              <span>{formatDate(user.createdAt)}</span>
            </div>
          </div>

          <div className={styles.formActions}>
            {isEditing ? (
              <>
                <button className={styles.btnSecondary} onClick={handleCancel}>
                  {t('common.cancel')}
                </button>
                <button
                  className={styles.btnPrimary}
                  onClick={handleSave}
                  disabled={updateMutation.isPending}
                >
                  {updateMutation.isPending ? t('common.saving') : t('common.save')}
                </button>
              </>
            ) : (
              <button className={styles.btnPrimary} onClick={() => setIsEditing(true)}>
                {t('common.edit')}
              </button>
            )}
          </div>
        </div>
      )}

      {activeTab === 'loginHistory' && (
        <div className={styles.card}>
          {historyLoading ? (
            <div className={styles.loadingState}>{t('common.loading')}</div>
          ) : historyEntries.length === 0 ? (
            <div className={styles.emptyState}>
              <p>{t('users.noLoginHistory')}</p>
            </div>
          ) : (
            <>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>{t('users.loginTime')}</th>
                    <th>{t('users.loginType')}</th>
                    <th>{t('users.loginStatus')}</th>
                    <th>{t('users.sourceIp')}</th>
                    <th>{t('users.userAgent')}</th>
                  </tr>
                </thead>
                <tbody>
                  {historyEntries.map((entry) => (
                    <tr key={entry.id}>
                      <td>{formatDate(entry.loginTime)}</td>
                      <td>{entry.loginType}</td>
                      <td><LoginStatusLabel status={entry.status} /></td>
                      <td>{entry.sourceIp}</td>
                      <td style={{ maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {entry.userAgent}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {historyTotalPages > 1 && (
                <div className={styles.pagination}>
                  <button
                    disabled={historyPage === 0}
                    onClick={() => setHistoryPage((p) => Math.max(0, p - 1))}
                    className={styles.btnSmall}
                  >
                    {t('common.previous')}
                  </button>
                  <span>
                    {t('common.pageOf', { current: historyPage + 1, total: historyTotalPages })}
                  </span>
                  <button
                    disabled={historyPage >= historyTotalPages - 1}
                    onClick={() => setHistoryPage((p) => p + 1)}
                    className={styles.btnSmall}
                  >
                    {t('common.next')}
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
